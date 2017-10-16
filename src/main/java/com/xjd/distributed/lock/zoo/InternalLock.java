package com.xjd.distributed.lock.zoo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.extern.slf4j.Slf4j;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import com.xjd.distributed.lock.DistributedLockException;
import com.xjd.utils.basic.LockUtils;
import com.xjd.utils.basic.lock.ABLock;
import com.xjd.utils.basic.lock.impl.StateABLock;

/**
 * @author elvis.xu
 * @since 2017-10-12 15:33
 */
@Slf4j
public class InternalLock {
	public static final Charset DEFAULT_CHARSET = Charset.forName("utf8");

	protected LockTree lockTree;
	protected CuratorFramework curatorFramework;
	protected ExecutorService executorService;
	protected ScheduledExecutorService scheduledExecutorService;
	protected TreeCache treeCache;

	protected String lockerPath;

	protected Map<String, LockTree.LocalNode> localNodeMap;
	protected Lock localNodeMapLock;
	protected Map<String, LockTree.LocalLock> localLockMap;
	protected ABLock localLockMapABLock;
	protected ThreadLocal<Map<String, LockTree.LocalLock>> localLockThreadLocal;

	protected InternalLock(String rootPath, CuratorFramework curatorFramework) {
		this.curatorFramework = curatorFramework;
		lockTree = new LockTree(rootPath);
		String ip = "";
		try {
			ip = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			log.warn("can not get the local ip address.");
		}
		lockerPath = lockTree.getLockersPath() + "/" + ip + "_" + UUID.randomUUID().toString().replace("-", "");
	}

	protected void start() {
		// 初始化node结构哦
		try {
			curatorFramework.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(lockTree.getLocksPath());
		} catch (KeeperException.NodeExistsException e) { // 已存在
			// do-nothing
		} catch (Exception e) {
			throw new ZooDistributedLockException(e);
		}
		try {
			curatorFramework.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(lockTree.getLockersPath());
		} catch (KeeperException.NodeExistsException e) { // 已存在
			// do-nothing
		} catch (Exception e) {
			throw new ZooDistributedLockException(e);
		}

		// 注册自己的lockerPath哦
		try {
			curatorFramework.create().withMode(CreateMode.EPHEMERAL).forPath(lockerPath);
		} catch (Exception e) {
			throw new ZooDistributedLockException(e);
		}

		executorService = Executors.newSingleThreadExecutor(); // 单线程更新哦
		scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
		localNodeMap = new HashMap<>();
		localNodeMapLock = new ReentrantLock();
		localLockMap = new ConcurrentHashMap<>();
		localLockMapABLock = new StateABLock();
		localLockThreadLocal = new ThreadLocal<>();

		// 启动监听 不用等待完成
		treeCache = new TreeCache(curatorFramework, lockTree.getPath());
		treeCache.getListenable().addListener((client, event) -> {
			processTreeCacheEvent(client, event);
		}, this.executorService);
		try {
			treeCache.start();
		} catch (Exception e) {
			throw new ZooDistributedLockException(e);
		}
		log.info("zoo distributed lock {} started!", lockerPath);
	}

	protected void close() {
		// 释放所有锁
		for (LockTree.LocalLock localLock : localLockMap.values()) {
			try {
				cancel(localLock);
			} catch (Exception e) {
				log.error("", e);
			}
		}

		// 删除lockerPath
		try {
			curatorFramework.delete().forPath(lockerPath);
		} catch (Exception e) {
			log.error("", e);
		}

		treeCache.close();
		scheduledExecutorService.shutdown();
		executorService.shutdown();

		localNodeMap = null;
		localNodeMapLock = null;
		localLockMap = null;
		localLockMapABLock = null;
		localLockThreadLocal = null;
		log.info("zoo distributed lock {} closed!", lockerPath);
	}

	protected void processTreeCacheEvent(CuratorFramework client, TreeCacheEvent event) {
		if (log.isTraceEnabled()) {
			log.trace("zoo distributed lock {} event: {}", lockTree.getPath(), event);
		}
		switch (event.getType()) {
		case NODE_ADDED:
		case NODE_UPDATED:
		case NODE_REMOVED:
			syncNode(event);
			break;
		case INITIALIZED:
		case CONNECTION_SUSPENDED:
		case CONNECTION_LOST:
			// XJD 重建TreeCache
		case CONNECTION_RECONNECTED:
			// do-nothing
			break;
		}
	}

	protected void syncNode(TreeCacheEvent event) {
		String path = event.getData().getPath();
		String name = null;
		switch (judgeNodePos(path)) {
		case 0: // root
			name = name == null ? "root" : name;
		case 1: // locks
			name = name == null ? "locks" : name;
		case 4: // lockers
			name = name == null ? "lockers" : name;
			if (event.getType() == TreeCacheEvent.Type.NODE_REMOVED) {
				log.error("zoo distributed lock " + name + " path [" + path + "] has been removed, attention!!!");
				throw new ZooDistributedLockException("zoo distributed lock " + name + " path [" + path + "] has been removed, attention!!!");
			}
			break;

		case 2: // lock node
			LockTree.LockNode lockNode = lockTree.getLockNodeMap().get(path);
			if (event.getType() == TreeCacheEvent.Type.NODE_REMOVED) {
				if (lockNode != null && !lockNode.getLockLeafMap().isEmpty()) {
					String txt = "zoo distributed lock lock path [" + path + "] has been removed, but it has children locally " + Arrays.toString(lockNode.getLockLeafMap().keySet().toArray()) + ", attention!!!";
					log.error(txt);
					throw new ZooDistributedLockException(txt);
				}
				if (lockNode != null) {
					lockTree.getLockNodeMap().remove(path);
				}
			} else {
				if (lockNode == null) {
					lockNode = new LockTree.LockNode(path);
					lockTree.getLockNodeMap().put(path, lockNode);
				}
				break;
			}
			break;

		case 3: // lock leaf
			syncLockLeaf(event);
			break;

		case 5: // locker leaf
			if (event.getType() == TreeCacheEvent.Type.NODE_REMOVED && path.equals(lockerPath)) {
				log.warn("zoo distributed lock locker path of self [" + path + "] has been removed, attention!");
			}
			break;
		case -1: // unknown
			log.debug("unknown path: {}", path);
			break;
		}
	}

	/**
	 * @param path
	 * @return -1--unknown, 0-root, 1-locks, 2-lock node(locks's children), 3-lock node's children, 4-lockers, 5-lockers's children
	 */
	protected int judgeNodePos(String path) {
		if (path == null) return -1;

		if (path.startsWith(lockTree.getLocksPath())) {
			if (path.length() == lockTree.getLocksPath().length()) {
				return 1;
			} else {
				if (path.substring(lockTree.getLocksPath().length()).lastIndexOf('/') == 0) {
					return 2;
				} else {
					return 3;
				}
			}
		} else if (path.startsWith(lockTree.getLockersPath())) {
			if (path.length() == lockTree.getLockersPath().length()) {
				return 4;
			} else {
				return 5;
			}
		} else if (path.equals(lockTree.getPath())) {
			return 0;
		}
		return -1;
	}

	protected LockTree.LockNode getRefLockNode(String path) {
		int index = path.lastIndexOf('/');
		if (index == lockTree.getLocksPath().length()) {
			return lockTree.getLockNodeMap().get(path);
		} else {
			return lockTree.getLockNodeMap().get(path.substring(0, index));
		}
	}

	protected void syncLockLeaf(TreeCacheEvent event) {
		String path = event.getData().getPath();
		if (event.getType() == TreeCacheEvent.Type.NODE_UPDATED) return; // 不处理update事件

		LockTree.LockNode refLockNode = getRefLockNode(path);
		refLockNode.setLockLeafCount(event.getType() == TreeCacheEvent.Type.NODE_ADDED ? refLockNode.getLockLeafCount() + 1 : refLockNode.getLockLeafCount() - 1);
		LockTree.LocalNode localNode = localNodeMap.get(refLockNode.getPath());

		if (event.getType() == TreeCacheEvent.Type.NODE_ADDED) {
			// 与本地比对啦, 有就要设置排名，并看能不能取得锁啊
			LockTree.LocalLock localLock = getLocalLockWithLock(path, localLockMapABLock.lockA());
			if (localLock == null) { // 本地没有对应的
				// do-nothing
			} else { // 本地有对应的
				// 添加lockLeaf并设置排名
				LockTree.LockLeaf lockLeaf = new LockTree.LockLeaf(path);
				lockLeaf.setRanking(refLockNode.getLockLeafCount());
				lockLeaf.setLocalLock(localLock);
				refLockNode.getLockLeafMap().put(path, lockLeaf);
				processLock(localNode, lockLeaf);
				localLock.getInitLatch().countDown(); // 初始化完成
			}

		} else {
			// 与本地比对啦, 有就要把锁改为非锁定状态啦
			// 修改本地其它的排名，并看能不能取得锁啊
			LockTree.LockLeaf lockLeaf = refLockNode.getLockLeafMap().get(path);
			Iterator<Map.Entry<String, LockTree.LockLeaf>> iterator = refLockNode.getLockLeafMap().entrySet().iterator();
			boolean found = false;
			while (iterator.hasNext()) {
				Map.Entry<String, LockTree.LockLeaf> entry = iterator.next();
				LockTree.LockLeaf otherLeaf = entry.getValue();
				if (found) {
					otherLeaf.setRanking(otherLeaf.getRanking() - 1);
					// 处理本节点
					processLock(localNode, otherLeaf);
					continue;
				}
				if (lockLeaf != null) {
					if (lockLeaf == otherLeaf) {
						found = true;
					}
				} else {
					if (path.compareTo(otherLeaf.getPath()) < 0) {
						found = true;
						otherLeaf.setRanking(otherLeaf.getRanking() - 1);
						// 处理本节点
						processLock(localNode, otherLeaf);
					}
				}
			}
			if (lockLeaf != null) {
				processUnLock(lockLeaf);
				refLockNode.getLockLeafMap().remove(path);
				localLockMap.remove(path);
			}

		}
	}

	protected void processLock(LockTree.LocalNode localNode, LockTree.LockLeaf lockLeaf) {
		LockTree.LocalLock localLock = lockLeaf.getLocalLock();
		if (lockLeaf.getRanking() <= localNode.getMaxConcurrent()) {
			if (localLock.getStatus() >= 2) return;
			try (LockUtils.LockResource lr = LockUtils.lock(localLock.getStatusLock())) {
				if (localLock.getStatus() >= 2) return;
				localLock.setStatus(2);
				if (log.isDebugEnabled()) {
					log.debug("zoo distributed lock {} locked!", localLock.getPath());
				}
				// 过期时间任务
				if (localLock.getExpireInMillis() > 0) {
					localLock.setExpireTaskFuture(scheduledExecutorService.schedule(() -> {
						expire(localLock);
					}, localLock.getExpireInMillis(), TimeUnit.MILLISECONDS));
				}
			}
			localLock.getLockLatch().countDown();

		}
	}

	protected void processUnLock(LockTree.LockLeaf lockLeaf) {
		try (LockUtils.LockResource lr = LockUtils.lock(lockLeaf.getLocalLock().getStatusLock())) {
			if (lockLeaf.getLocalLock().getStatus() >= 3) return;
			lockLeaf.getLocalLock().setStatus(3);
		}
	}

	protected void expire(LockTree.LocalLock localLock) {
		try (LockUtils.LockResource lr = LockUtils.lock(localLock.getStatusLock())) {
			if (localLock.getStatus() >= 3) return;
			localLock.setStatus(5);
		}
		try {
			curatorFramework.delete().forPath(localLock.getPath());
		} catch (KeeperException.NoNodeException e) {
			// do-nothing
		} catch (Exception e) {
			throw new ZooDistributedLockException(e);
		}
		if (log.isDebugEnabled()) {
			log.debug("zoo distributed lock {} expired!", localLock.getPath());
		}
	}

	protected LockTree.LocalLock getLocalLockWithLock(String path, Lock lock) {
		try (LockUtils.LockResource lr = LockUtils.lock(lock)) {
			LockTree.LocalLock localLock = localLockMap.get(path);
			return localLock;
		}
	}

	/**
	 * @param name            the identified name for the lock, so different locks should have different names.
	 * @param timeoutInMillis the timeout in milliseconds before getting the lock. {@code <0} means waiting infinitely;
	 *                        {@code 0} means trying to get the lock immediately.
	 * @param expireInMillis  the expire time in milliseconds of the lock. {@code <=0} means unlimited.
	 * @param maxConcurrent   the max number lock to have the lock in meantime. it must be positive number.
	 * @param maxQueue        the max number lock to wait in queue for the lock. {@code <0} means unlimited; {@code 0} means no queue.
	 * @param interrupted     {@code true} means interrupted.
	 * @return {@code true} if locked, {@code false} otherwise.
	 */
	protected boolean lock(String name, long timeoutInMillis, long expireInMillis, int maxConcurrent, int maxQueue, boolean interrupted) throws InterruptedException {
		// 为name对应的LockNode设置配置信息，若已有则比对配置，目前要求同一个应用内的配置是一样的，跨应用可以不一样（需要业务自己保证配置一致性，不作校验）
		String nodePath = lockTree.getLocksPath() + "/" + namePreprocess(name);
		LockTree.LocalNode newNode = new LockTree.LocalNode(nodePath, maxConcurrent, maxQueue);
		LockTree.LocalNode localNode = localNodeMap.get(nodePath);
		if (localNode == null) {
			try (LockUtils.LockResource lr = LockUtils.lock(localNodeMapLock)) {
				localNode = localNodeMap.get(nodePath);
				if (localNode == null) {
					localNode = newNode;
					localNodeMap.put(nodePath, localNode);
				}
			}
		}
		if (!localNode.equals(newNode)) {
			throw new DistributedLockException.ConfigNotMatchException("lock path: " + nodePath + ", expected: " + localNode.toString() + ", but: " + newNode.toString());
		}

		LockTree.LockNode lockNode = lockTree.getLockNodeMap().get(nodePath);
		// 看看数量有没有超标，超限直接返回喽
		if (lockNode != null && maxQueue >= 0 && lockNode.getLockLeafCount() >= (maxConcurrent + maxQueue)) {
			return false;
		}

		// 选初始化LockNode节点哦
		if (lockNode == null) { // 增加点性能
			try {
				curatorFramework.create().withMode(CreateMode.PERSISTENT).forPath(nodePath);
			} catch (KeeperException.NodeExistsException e) { // 已存在
				// do-nothing
			} catch (Exception e) {
				throw new ZooDistributedLockException(e);
			}
		}

		LockTree.LocalLock localLock = new LockTree.LocalLock();
		try (LockUtils.LockResource lr = LockUtils.lock(localLockMapABLock.lockB())) {
			try {
				String path = curatorFramework.create().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(nodePath + "/" + LockTree.LockLeaf.NAME_PREFIX);
				localLock.setPath(path);
				localLock.setExpireInMillis(expireInMillis);
				localLockMap.put(path, localLock);
				Map<String, LockTree.LocalLock> tmpMap = localLockThreadLocal.get();
				if (tmpMap == null) {
					tmpMap = new HashMap<>();
					localLockThreadLocal.set(tmpMap);
				}
				tmpMap.put(nodePath, localLock);
			} catch (Exception e) {
				throw new ZooDistributedLockException(e);
			}
		}

		try {
			localLock.getInitLatch().await(1, TimeUnit.SECONDS); // 无论如何总得等人家初始化一下吧，要不然你来锁什么
		} catch (InterruptedException e) {
			// do-nothing
		}

		long start = System.currentTimeMillis();
		long remain = timeoutInMillis;
		InterruptedException exception = null;
		while (true) {
			try {
				if (timeoutInMillis < 0) {
					localLock.getLockLatch().await();
					break;
				} else {
					remain = remain - (System.currentTimeMillis() - start);
					localLock.getLockLatch().await(remain, TimeUnit.MILLISECONDS);
					break;
				}
			} catch (InterruptedException e) {
				if (interrupted) {
					exception = e;
					break;
				}
			}
		}


		boolean cancel = false;
		if (exception != null) {
			cancel = true;
		} else {
			try (LockUtils.LockResource lr = LockUtils.lock(localLock.getStatusLock())) {
				if (localLock.getStatus() != 2) { // 未lock
					cancel = true;
				}
			}
		}

		if (cancel) {
			localLockThreadLocal.get().remove(nodePath);
			cancel(localLock);
		}

		if (exception != null) throw exception;

		return cancel ? false : true;
	}

	protected void cancel(LockTree.LocalLock localLock) {
		try (LockUtils.LockResource lr = LockUtils.lock(localLock.getStatusLock())) {
			int status = localLock.getStatus();
			localLock.setStatus(4);
			if (status >= 3) return;
		}
		try {
			curatorFramework.delete().forPath(localLock.getPath());
		} catch (KeeperException.NoNodeException e) {
			// do-nothing
		} catch (Exception e) {
			throw new ZooDistributedLockException(e);
		}
		Future future = localLock.getExpireTaskFuture();
		if (future != null) {
			future.cancel(true);
		}
		if (log.isDebugEnabled()) {
			log.debug("zoo distributed lock {} cancelled!", localLock.getPath());
		}
	}

	protected void unlock(String name) {
		Map<String, LockTree.LocalLock> map = localLockThreadLocal.get();
		if (map == null) return;
		String nodePath = lockTree.getLocksPath() + "/" + namePreprocess(name);
		LockTree.LocalLock localLock = map.get(nodePath);
		if (localLock == null) return;

		localLockThreadLocal.get().remove(nodePath);

		try (LockUtils.LockResource lr = LockUtils.lock(localLock.getStatusLock())) {
			if (localLock.getStatus() >= 3) return;
			localLock.setStatus(3);
		}

		try {
			curatorFramework.delete().forPath(localLock.getPath());
		} catch (KeeperException.NoNodeException e) {
			// do-nothing
		} catch (Exception e) {
			throw new ZooDistributedLockException(e);
		}

		Future future = localLock.getExpireTaskFuture();
		if (future != null) {
			future.cancel(true);
		}

		if (log.isDebugEnabled()) {
			log.debug("zoo distributed lock {} unlocked!", localLock.getPath());
		}
	}


	protected String namePreprocess(String name) {
		if (name == null) return name;
		return name.replace('/', ':');
	}

	protected boolean isLocked(String name) {
		Map<String, LockTree.LocalLock> map = localLockThreadLocal.get();
		if (map == null) return false;
		String nodePath = lockTree.getLocksPath() + "/" + namePreprocess(name);
		LockTree.LocalLock localLock = map.get(nodePath);
		if (localLock == null) return false;

		try (LockUtils.LockResource lr = LockUtils.lock(localLock.getStatusLock())) {
			if (localLock.getStatus() == 2) return true;
		}
		return false;
	}
}
