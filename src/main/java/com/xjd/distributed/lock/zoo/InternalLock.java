package com.xjd.distributed.lock.zoo;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import lombok.extern.slf4j.Slf4j;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.transaction.CuratorTransactionResult;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;

import com.xjd.distributed.lock.DistributedLockException;
import com.xjd.utils.basic.AssertUtils;
import com.xjd.utils.basic.JsonUtils;
import com.xjd.utils.basic.StringUtils;

/**
 * @author elvis.xu
 * @since 2017-09-04 09:40
 */
@Slf4j
public class InternalLock {
	protected String namespace;
	protected String path;
	protected CuratorFramework curatorFramework;
	protected int maxConcurrent;
	protected long maxExpireInMills;

	protected String realmPath;
	protected String locksPath;

	/** 0-latent(not start); 1-started; 2-closed */
	protected volatile int status;
	protected ReadWriteLock statusLock;

	protected Map<String, LockNodeInMem> lockedNodes;

	protected LinkedList<LockNodeInMem> queueNodes;
	protected ReadWriteLock queueNodesLock;
	protected Condition queueNodesWriteLockCondition;

	protected ThreadLocal<LockNodeInMem> threadLocal;

	protected ScheduledExecutorService scheduledExecutorService;
	protected boolean innerScheduledExecutorService = false;

	protected TreeCache treeCache; // 用于监听zoo节点变化从而控制锁

	protected ScheduledFuture cleanupTaskFuture;  // 用于清扫过期的锁
	protected ReadWriteLock cleanupTaskLock;

	public InternalLock(String namespace, String path, CuratorFramework curatorFramework, int maxConcurrent, long maxExpireInMills, ScheduledExecutorService scheduledExecutorService) {
		AssertUtils.assertArgumentNonBlank(path, "path can not be blank.");
		AssertUtils.assertArgumentNonNull(curatorFramework, "curatorFramework can not be null.");
		AssertUtils.assertArgumentGreaterEqualThan(maxConcurrent, 1, "maxConcurrent must greater than 0");
		AssertUtils.assertArgumentGreaterEqualThan(maxExpireInMills, 1, "maxExpireInMills must greater than 0");
		if (!path.startsWith("/")) {
			throw new IllegalArgumentException("path must be a valid zoo path");
		}
		this.namespace = StringUtils.trimToEmpty(namespace);
		this.path = path;
		this.curatorFramework = curatorFramework;
		this.maxConcurrent = maxConcurrent;
		this.maxExpireInMills = maxExpireInMills;

		this.realmPath = this.path;
		this.locksPath = this.realmPath + "/locks";

		this.status = 0;
		this.statusLock = new ReentrantReadWriteLock();

		this.lockedNodes = new ConcurrentHashMap<>();

		this.queueNodes = new LinkedList<>();
		this.queueNodesLock = new ReentrantReadWriteLock();
		this.queueNodesWriteLockCondition = queueNodesLock.writeLock().newCondition();

		this.threadLocal = new ThreadLocal<>();

		this.scheduledExecutorService = scheduledExecutorService != null ? scheduledExecutorService : Executors.newScheduledThreadPool(2);
		this.innerScheduledExecutorService = scheduledExecutorService != null ? false : true;

		this.cleanupTaskLock = new ReentrantReadWriteLock();
	}

	protected void assertNotClosed() {
		if (status == 2) {
			throw new ZooDistributedLockException.LockClosedException();
		}
	}

	protected void assertRunning() {
		if (status != 1) {
			throw new ZooDistributedLockException.LockNotRunningException();
		}
	}

	public void start() {
		assertNotClosed();
		if (status != 0) return;
		statusLock.writeLock().lock();
		try {
			assertNotClosed();
			if (status != 0) return;
			doStart();
			status = 1;
		} finally {
			statusLock.writeLock().unlock();
		}
	}


	protected void doStart() {
		try {
			RealmNodeConfig realmNodeConfig = RealmNodeConfig.builder().maxConcurrent(maxConcurrent).maxExpireInMills(maxExpireInMills).build();

			Stat realmStat = curatorFramework.checkExists().forPath(realmPath);
			// 节点不存在, 创建之
			if (realmStat == null) {
				try {
					curatorFramework.transaction().forOperations(
							curatorFramework.transactionOp().create().withMode(CreateMode.PERSISTENT).forPath(realmPath, JsonUtils.toJson(realmNodeConfig).getBytes(Charset.forName("UTF-8"))),
							curatorFramework.transactionOp().create().withMode(CreateMode.PERSISTENT).forPath(locksPath, JsonUtils.toJson(new LocksNodeData()).getBytes(Charset.forName("UTF-8")))
					);
				} catch (KeeperException.NodeExistsException e) {
					// 已被其它锁创建
				}
			}

			// 判断并比较现有节点的配置是否一致
			realmStat = new Stat();
			RealmNodeConfig existRealmNodeConfig;
			try {
				existRealmNodeConfig = getPathData(realmPath, realmStat, RealmNodeConfig.class);
			} catch (JsonUtils.JsonException e) {
				throw new ZooDistributedLockException("path '" + realmPath + "' is not a lock.");
			}
			if (!realmNodeConfig.equals(existRealmNodeConfig)) {
				throw new DistributedLockException.ConfigNotMatchException("the config of this lock does not match the config of zoo lock path '" + realmPath + "'");
			}

			// 创建watch
			log.debug("create tree cache of path '{}'", locksPath);
			treeCache = new TreeCache(curatorFramework, locksPath);
			treeCache.getListenable().addListener((client, event) -> {
				processLocksTreeCacheEvent(event);
			}, scheduledExecutorService);
			treeCache.start();

			// 是否需要启动清扫任务
			startCleanupTask();

		} catch (DistributedLockException e) {
			throw e;
		} catch (Exception e) {
			throw new ZooDistributedLockException("init lock '" + realmPath + "' failed.", e);
		}
	}

	protected <T> T getPathData(String path, Stat stat, Class<T> clazz) throws Exception {
		byte[] data = null;
		if (stat == null) {
			data = curatorFramework.getData().forPath(path);
		} else {
			data = curatorFramework.getData().storingStatIn(stat).forPath(path);
		}
		String dataTxt = new String(data, Charset.forName("UTF-8"));
		if (clazz.isAssignableFrom(String.class)) {
			return (T) dataTxt;
		}
		return JsonUtils.fromJson(dataTxt, clazz);
	}

	protected void processLocksTreeCacheEvent(TreeCacheEvent event) {
		if (log.isTraceEnabled()) {
			log.trace("TreeCacheEvent: {}", JsonUtils.toJson(event));
		}
		switch (event.getType()) {
		case NODE_REMOVED:
			lockedNodes.remove(event.getData().getPath());
			signalQueueNodes();
			break;
		case NODE_UPDATED: // 看是否需要启动过期扫描任务
			if (event.getData().getPath().equals(locksPath) && event.getData().getStat().getNumChildren() >= maxConcurrent) {
				startCleanupTask();
			}
			break;
		case CONNECTION_SUSPENDED:
		case CONNECTION_LOST:
			log.warn("zookeeper connection suspended or lost, the lock may invalid: {}", JsonUtils.toJson(event));
			break;
		case CONNECTION_RECONNECTED:
			log.info("zookeeper connection reconnected: {}", JsonUtils.toJson(event));
			break;
		}
	}

	protected void startCleanupTask() {
		if (cleanupTaskFuture != null) {
			return;
		}
		if (status == 2) { // 已关闭
			return;
		}
		cleanupTaskLock.writeLock().lock();
		try {
			if (cleanupTaskFuture != null) {
				return;
			}
			if (status == 2) { // 已关闭
				return;
			}
			// 启动任务
//			log.debug("starting cleanup task...");
			Stat locksStat = new Stat();
			List<String> childNames = curatorFramework.getChildren().storingStatIn(locksStat).forPath(locksPath);
			if (locksStat.getNumChildren() < maxConcurrent) {
				return;
			}
			Collections.sort(childNames);
			String[] parseNames = parseLockNodeName(childNames.get(0));
			long remain = Long.parseLong(parseNames[0]) + maxExpireInMills - System.currentTimeMillis();
			remain = remain < 0 ? 0 : remain;
			cleanupTaskFuture = scheduledExecutorService.schedule(() -> {
				doCleanupTask();
			}, remain, TimeUnit.MILLISECONDS);
			log.debug("scheduled cleanup task delay: {}ms", remain);
		} catch (DistributedLockException e) {
			throw e;
		} catch (Throwable t) {
			throw new ZooDistributedLockException(t);
		} finally {
			cleanupTaskLock.writeLock().unlock();
		}
	}

	protected void doCleanupTask() {
		try {
			log.debug("exec cleanup task...");
			Stat locksStat = new Stat();
			List<String> childNames = curatorFramework.getChildren().storingStatIn(locksStat).forPath(locksPath);
			Collections.sort(childNames);
			for (String childName : childNames) {
				String[] parseNames = parseLockNodeName(childNames.get(0));
				long remain = Long.parseLong(parseNames[0]) + maxExpireInMills - System.currentTimeMillis();
				if (remain <= 0) {
					// 删除
					deleteLockNode(locksPath + "/" + childName);
				} else {
					break;
				}
			}
		} catch (DistributedLockException e) {
			throw e;
		} catch (Throwable t) {
			throw new ZooDistributedLockException(t);
		}
		cleanupTaskFuture = null;
		startCleanupTask();
	}

	public void close() {
		if (status == 2) return;
		assertRunning();
		statusLock.writeLock().lock();
		try {
			if (status == 2) return;
			assertRunning();
			status = 2;
		} finally {
			statusLock.writeLock().unlock();
		}

		log.debug("lock closing...: {}", namespace);

		// 关闭监听
		treeCache.close();

		// 先换醒所有等待锁的线程, 它们将抛出锁已关闭异常
		queueNodesLock.writeLock().lock();
		try {
			queueNodesWriteLockCondition.signalAll();
		} finally {
			queueNodesLock.writeLock().unlock();
		}

		// 释放全部锁
		for (String path : lockedNodes.keySet()) {
			doUnlock(path);
		}

		// 关闭清理任务
		if (cleanupTaskFuture != null) {
			cleanupTaskLock.writeLock().lock();
			try {
				if (cleanupTaskFuture != null) {
					cleanupTaskFuture.cancel(true);
					cleanupTaskFuture = null;
				}
			} finally {
				cleanupTaskLock.writeLock().unlock();
			}
		}

		// 关闭内部调度器
		if (innerScheduledExecutorService) {
			scheduledExecutorService.shutdown();
		}
	}

	public int getNowConcurrent() {
		assertRunning();
		Stat stat = null;
		try {
			stat = curatorFramework.checkExists().forPath(locksPath);
		} catch (Exception e) {
			throw new ZooDistributedLockException("", e);
		}
		return stat.getNumChildren();
	}

	protected String[] parseLockNodeName(String name) {
		return name.substring(name.lastIndexOf('/') + 1).split("_", 3);
	}

	public long getExpireInMills() {
		assertRunning();
		LockNodeInMem lockNodeInMem = threadLocal.get();
		if (lockNodeInMem == null) {
			return -1;
		}
		String[] parseNames = parseLockNodeName(lockNodeInMem.getPath());
		long remainExpireTime = Long.parseLong(parseNames[0]) + maxExpireInMills - System.currentTimeMillis();
		return remainExpireTime < 0 ? 0 : remainExpireTime;
	}

	public boolean isLocked() {
		LockNodeInMem lockNodeInMem = threadLocal.get();
		if (lockNodeInMem == null) {
			return false;
		}
		if (lockedNodes.get(lockNodeInMem.getPath()) == null) {
			return false;
		}
		return true;
	}

	/**
	 * @param time        小于0表无限等待, 0表立即返回, 大于0为具体时间
	 * @param unit
	 * @param interrupted
	 * @return
	 * @throws InterruptedException
	 */
	public boolean lock(long time, TimeUnit unit, boolean interrupted) throws InterruptedException {
		assertRunning();
		if (threadLocal.get() != null) {
			return true;
		}
		LockNodeInMem lockNodeInMem = new LockNodeInMem().setInterrupted(interrupted).setWaitingTime(time).setWaitingTimeUnit(unit);
		threadLocal.set(lockNodeInMem);
		try {
			return lock(lockNodeInMem);
		} catch (Throwable t) {
			threadLocal.remove();
			if (t instanceof InterruptedException) {
				throw (InterruptedException) t;
			} else if (t instanceof RuntimeException) {
				throw (RuntimeException) t;
			} else {
				// impossible
				throw new RuntimeException(t);
			}
		}
	}

	protected boolean lock(LockNodeInMem lockNodeInMem) throws InterruptedException {
		assertRunning();
		// 尝试立即获取
		if (lockImmediatly(lockNodeInMem)) {
			return true; // 获取成功
		}

		// 是否不想等待
		if (lockNodeInMem.getWaitingTime() == 0L) {
			return false;
		}

		// 在队列中等待获取锁
		return lockInQueue(lockNodeInMem);
	}

	protected boolean lockImmediatly(LockNodeInMem lockNodeInMem) {
		try {
			Stat locksStat = new Stat();
			LocksNodeData locksData = getPathData(locksPath, locksStat, LocksNodeData.class);
			if (locksStat.getNumChildren() < maxConcurrent) {
				// 当前锁节点数小于最大允许并发锁数, 尝试锁
				String lockPath = getLockNodePath();
				List<CuratorTransactionResult> results;
				try {
					results = curatorFramework.transaction().forOperations(
							curatorFramework.transactionOp().create().withTtl(maxExpireInMills).withMode(CreateMode.PERSISTENT_WITH_TTL).forPath(lockPath),
							curatorFramework.transactionOp().setData().withVersion(locksStat.getVersion()).forPath(locksPath, JsonUtils.toJson(locksData).getBytes(Charset.forName("UTF-8")))
					);
				} catch (KeeperException.BadVersionException e) {
					// 有并发，再试
					lockNodeInMem.setRetryTimes(lockNodeInMem.getRetryTimes() + 1);
					return lockImmediatly(lockNodeInMem);
				}
				// 能走到这里说明获取成功了
				lockNodeInMem.setPath(lockPath);
				lockedNodes.put(lockPath, lockNodeInMem);
				log.debug("lock '{}' get lock", lockPath);
				return true;
			}
			return false;
		} catch (Throwable t) {
			throw new ZooDistributedLockException(t);
		}
	}

	protected boolean lockInQueue(LockNodeInMem lockNodeInMem) throws InterruptedException {
		boolean retry = false;
		queueNodesLock.writeLock().lock();
		try {
			// 先入队列
			queueNodes.addLast(lockNodeInMem);

			Stat locksStat = new Stat();
			LocksNodeData locksData = getPathData(locksPath, locksStat, LocksNodeData.class);
			if (locksStat.getNumChildren() < maxConcurrent) {
				// 当前锁节点数小于最大允许并发锁数, 重试
				queueNodes.removeLast();
				retry = true;

			} else {
				// 需要等待
				boolean awaitResult = awaitQueueNodesLock(lockNodeInMem);
				if (!awaitResult) {
					// 时间耗尽没有通知
					queueNodes.remove(lockNodeInMem);
					return false;

				} else {
					queueNodes.remove(lockNodeInMem);
					retry = true;
				}
			}

		} catch (InterruptedException e) {
			throw e;

		} catch (Exception e) {
			throw new ZooDistributedLockException(e);

		} finally {
			queueNodesLock.writeLock().unlock();
		}

		if (retry) {
			lockNodeInMem.setRetryTimes(lockNodeInMem.getRetryTimes() + 1);
			return lock(lockNodeInMem);
		} else {
			return false;
		}
	}

	protected String getLockNodePath() {
		return locksPath + "/" + System.currentTimeMillis() + "_" + namespace + "_" + UUID.randomUUID().toString().replace("-", "");
	}

	/**
	 * @param lockNodeInMem
	 * @return {@code false} 为等待超时, {@code true} 为接到换醒信号
	 * @throws InterruptedException
	 */
	protected boolean awaitQueueNodesLock(LockNodeInMem lockNodeInMem) throws InterruptedException {
		if (lockNodeInMem.getWaitingTime() > 0) {
			long remain = lockNodeInMem.getCreateTimeInMills() + lockNodeInMem.getWaitingTimeUnit().toMillis(lockNodeInMem.getWaitingTime()) - System.currentTimeMillis();
			if (remain <= 0) { // 等待超时了
				return false;
			} else {
				try {
					log.debug("lock waiting: {}ms", remain);
					return queueNodesWriteLockCondition.await(remain, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					if (lockNodeInMem.isInterrupted()) {
						throw e;
					} else {
						return awaitQueueNodesLock(lockNodeInMem);
					}
				}
			}

		} else if (lockNodeInMem.getWaitingTime() < 0) {
			// 无限等待
			try {
				log.debug("lock waiting unlimitly");
				queueNodesWriteLockCondition.await();
				return true;
			} catch (InterruptedException e) {
				if (lockNodeInMem.isInterrupted()) {
					throw e;
				} else {
					return awaitQueueNodesLock(lockNodeInMem);
				}
			}

		} else {
			return false;
		}
	}

	public void unlock() {
		LockNodeInMem lockNodeInMem = threadLocal.get();
		if (lockNodeInMem == null) {
			return;
		}
		threadLocal.remove();
		doUnlock(lockNodeInMem.getPath());
	}

	protected void doUnlock(String path) {
		log.debug("unlock '{}'", path);
		lockedNodes.remove(path);
		deleteLockNode(path);
	}

	protected void signalQueueNodes() {
		if (!queueNodes.isEmpty()) {
			queueNodesLock.writeLock().lock();
			try {
				if (!queueNodes.isEmpty()) {
					queueNodesWriteLockCondition.signal();
				}
			} finally {
				queueNodesLock.writeLock().unlock();
			}
		}
	}

	protected void deleteLockNode(String path) {
		try {
			log.debug("deleting lock node: {}", path);
			curatorFramework.delete().forPath(path);
			signalQueueNodes();
		} catch (KeeperException.NoNodeException e) {
			// do-nothing  已删除
		} catch (Exception e) {
			new ZooDistributedLockException(e);
		}
	}
}
