package com.xjd.distributed.lock.zoo;

import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import lombok.extern.slf4j.Slf4j;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;

import com.xjd.distributed.lock.DistributedLock;
import com.xjd.distributed.lock.DistributedLockException;
import com.xjd.utils.basic.AssertUtils;
import com.xjd.utils.basic.JsonUtils;
import com.xjd.utils.basic.StringUtils;

/**
 * @author elvis.xu
 * @since 2017-08-29 17:16
 */
@Slf4j
public class ZooDistributedLock implements DistributedLock, AutoCloseable {
	protected String key;
	protected CuratorFramework curatorFramework;
	protected int maxConcurrent;
	protected long maxExpireInMills;

	protected String lockNode;

	/** 0-not start; 1-started; 2-closed */
	protected volatile int status;
	protected ReadWriteLock statusLock;

	protected TreeCache treeCache;
	protected ScheduledExecutorService scheduledExecutorService;
	protected String instanceName;
	protected LinkedList<OneLock> lockQueue;
	protected ReadWriteLock queueLock;
	protected ThreadLocal<OneLock> lockThreadLocal;
	protected ScheduledFuture cleanTaskFuture;
	protected ReadWriteLock cleanTaskLock;

	public ZooDistributedLock(String key, CuratorFramework curatorFramework, int maxConcurrent, long maxExpireInMills, ScheduledExecutorService scheduledExecutorService, String instanceName) {
		AssertUtils.assertArgumentNonBlank(key, "key must be set");
		AssertUtils.assertArgumentNonNull(curatorFramework, "curatorFramework must be set");
		AssertUtils.assertArgumentGreaterEqualThan(maxConcurrent, 1, "maxConcurrent must greater than 0");
		AssertUtils.assertArgumentGreaterEqualThan(maxExpireInMills, 1, "maxExpireInMills must greater than 0");
		if (!key.startsWith("/")) {
			throw new IllegalArgumentException("key must be a valid zookeeper path");
		}
		this.key = key;
		this.curatorFramework = curatorFramework;
		this.maxConcurrent = maxConcurrent;
		this.maxExpireInMills = maxExpireInMills;
		this.lockNode = this.key + "/locks";

		this.status = 0;
		this.statusLock = new ReentrantReadWriteLock();

		this.scheduledExecutorService = scheduledExecutorService != null ? scheduledExecutorService : Executors.newSingleThreadScheduledExecutor();
		this.instanceName = StringUtils.trimToEmpty(instanceName);
		this.lockQueue = new LinkedList<>();
		this.queueLock = new ReentrantReadWriteLock();
		this.lockThreadLocal = new ThreadLocal<>();
		this.cleanTaskLock = new ReentrantReadWriteLock();
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
			LockConfig lockConfig = LockConfig.builder().maxConcurrent(maxConcurrent).maxExpireInMills(maxExpireInMills).build();
			Stat stat = curatorFramework.checkExists().forPath(key);

			// 节点不存在, 创建之
			if (stat == null) {
				try {
					curatorFramework.transaction().forOperations(
							curatorFramework.transactionOp().create().withMode(CreateMode.PERSISTENT).forPath(key, JsonUtils.toJson(stat).getBytes(Charset.forName("UTF-8"))),
							curatorFramework.transactionOp().create().withMode(CreateMode.PERSISTENT).forPath(lockNode, JsonUtils.toJson(new LockNodeData()).getBytes(Charset.forName("UTF-8")))
					);
				} catch (KeeperException.NodeExistsException e) {
					// do nothing
				}
			}

			// 判断并比较现有节点的配置是否一致
			stat = new Stat();
			byte[] data = curatorFramework.getData().storingStatIn(stat).forPath(key);
			LockConfig existLockConfig;
			try {
				existLockConfig = JsonUtils.fromJson(new String(data, Charset.forName("UTF-8")), LockConfig.class);
			} catch (JsonUtils.JsonException e) {
				throw new ZooDistributedLockException("path '" + key + "' is not a lock node.");
			}
			if (!lockConfig.equals(existLockConfig)) {
				throw new DistributedLockException.ConfigNotMatchException("the config of this lock does not match the config of zoo lock path '" + key + "'");
			}

			// 创建watch
			treeCache = new TreeCache(curatorFramework, lockNode);
			treeCache.getListenable().addListener((client, event) -> {
				processTreeCacheEvent(event);
			}, scheduledExecutorService);
			treeCache.start();

		} catch (DistributedLockException e) {
			throw e;
		} catch (Exception e) {
			throw new ZooDistributedLockException("init lock '" + key + "' failed.", e);
		}
	}

	protected void processTreeCacheEvent(TreeCacheEvent event) {
		switch (event.getType()) {
		case NODE_REMOVED:
			queueLock.writeLock().lock();
			try {
				queueLock.writeLock().newCondition().signal();
			} finally {
				queueLock.writeLock().unlock();
			}
			break;
		case NODE_UPDATED: // 看是否需要启动过期扫描任务
			if (event.getData().getPath().equals(lockNode) && event.getData().getStat().getNumChildren() >= maxConcurrent) {
				cleanTaskLock.readLock().lock();
				try {
					if (cleanTaskFuture != null) {
						// FIXME
					}
				} finally {
					cleanTaskLock.readLock().unlock();
				}
			}
			break;
		}
	}

	@Override
	public void close() {

	}

	@Override
	public int getMaxConcurrent() {
		return maxConcurrent;
	}

	@Override
	public long getMaxExpireInMills() {
		return maxExpireInMills;
	}

	@Override
	public int getNowConcurrent() {
		assertRunning();
		Stat stat = null;
		try {
			stat = curatorFramework.checkExists().forPath(lockNode);
		} catch (Exception e) {
			throw new ZooDistributedLockException("", e);
		}
		return stat.getNumChildren();
	}

	@Override
	public long getExpireInMills() {
		OneLock oneLock = lockThreadLocal.get();
		if (oneLock == null) {
			return -1;
		}
		String[] parseNames = parseName(oneLock.node());
		long expireTime = Long.parseLong(parseNames[0]) + maxExpireInMills - System.currentTimeMillis();
		return expireTime < 0 ? 0 : expireTime;
	}

	@Override
	public boolean tryLock() {
		if (lockThreadLocal.get() != null) {
			return true;
		}
		OneLock oneLock = new OneLock().createTimeInMills(System.currentTimeMillis())
				.timeoutInMillis(0).interrupted(false).retryTimes(0);
		lockThreadLocal.set(oneLock);
		try {
			return doLock(oneLock);
		} catch (InterruptedException e) {
			lockThreadLocal.set(null);
			// impossible
		} catch (RuntimeException e) {
			lockThreadLocal.set(null);
			throw e;
		}
		return false;
	}

	@Override
	public boolean tryLock(long time, TimeUnit unit) {
		if (lockThreadLocal.get() != null) {
			return true;
		}
		OneLock oneLock = new OneLock().createTimeInMills(System.currentTimeMillis())
				.timeoutInMillis(unit.toMillis(time)).interrupted(false).retryTimes(0);
		lockThreadLocal.set(oneLock);
		try {
			return doLock(oneLock);
		} catch (InterruptedException e) {
			lockThreadLocal.set(null);
			// impossible
		} catch (RuntimeException e) {
			lockThreadLocal.set(null);
			throw e;
		}
		return false;
	}

	@Override
	public boolean tryLockInterruptibly(long time, TimeUnit unit) throws InterruptedException {
		if (lockThreadLocal.get() != null) {
			return true;
		}
		OneLock oneLock = new OneLock().createTimeInMills(System.currentTimeMillis())
				.timeoutInMillis(unit.toMillis(time)).interrupted(true).retryTimes(0);
		lockThreadLocal.set(oneLock);
		try {
			return doLock(oneLock);
		} catch (InterruptedException e) {
			lockThreadLocal.set(null);
			throw e;
		} catch (RuntimeException e) {
			lockThreadLocal.set(null);
			throw e;
		}
	}

	@Override
	public void lock() {
		if (lockThreadLocal.get() != null) {
			return;
		}
		OneLock oneLock = new OneLock().createTimeInMills(System.currentTimeMillis())
				.timeoutInMillis(-1).interrupted(false).retryTimes(0);
		lockThreadLocal.set(oneLock);
		try {
			doLock(oneLock);
		} catch (InterruptedException e) {
			lockThreadLocal.set(null);
			// impossible
		} catch (RuntimeException e) {
			lockThreadLocal.set(null);
			throw e;
		}
	}

	@Override
	public void lockInterruptibly() throws InterruptedException {
		if (lockThreadLocal.get() != null) {
			return;
		}
		OneLock oneLock = new OneLock().createTimeInMills(System.currentTimeMillis())
				.timeoutInMillis(-1).interrupted(true).retryTimes(0);
		lockThreadLocal.set(oneLock);
		try {
			doLock(oneLock);
		} catch (InterruptedException e) {
			lockThreadLocal.set(null);
			throw e;
		} catch (RuntimeException e) {
			lockThreadLocal.set(null);
			throw e;
		}
	}

	protected boolean doLock(OneLock oneLock) throws InterruptedException {
		// 尝试立即获取
		if (doLockNoWait(oneLock)) {
			return true; // 获取成功则返回, 否则继续
		}

		// 不能获取且不想等待
		if (oneLock.timeoutInMillis() == 0) {
			return false;
		}

		// 等待获取
		return doLockInQueue(oneLock);
	}

	protected String getLockName() {
		return System.currentTimeMillis() + "_" + instanceName + "_";
	}

	protected LockNodeData getLockNodeData(Stat stat) {
		try {
			byte[] data = curatorFramework.getData().storingStatIn(stat).forPath(lockNode);
			return JsonUtils.fromJson(new String(data, Charset.forName("UTF-8")), LockNodeData.class);
		} catch (Exception e) {
			throw new ZooDistributedLockException("get data of zoo node '" + lockNode + "' failed.", e);
		}
	}

	protected boolean doLockNoWait(OneLock oneLock) {
		try {
			Stat lockStat = new Stat();
			LockNodeData lockNodeData = getLockNodeData(lockStat);
			if (lockStat.getNumChildren() < maxConcurrent) { // 能获取锁直接获取
				String lockName = getLockName();
				lockNodeData.setLastOperateNode(lockName);
				try {
					curatorFramework.transaction().forOperations(
							curatorFramework.transactionOp().create().withTtl(maxExpireInMills).withMode(CreateMode.PERSISTENT_SEQUENTIAL_WITH_TTL).forPath(lockNode + "/" + lockName),
							curatorFramework.transactionOp().setData().withVersion(lockStat.getVersion()).forPath(lockNode, JsonUtils.toJson(lockNodeData).getBytes(Charset.forName("UTF-8")))
					);
				} catch (KeeperException.BadVersionException e) {
					// 有并发，再试
					oneLock.retryTimes(oneLock.retryTimes() + 1);
					return doLockNoWait(oneLock);
				}
				oneLock.node(lockName);
				return true; // 操作成功
			}
			return false;
		} catch (DistributedLockException e) {
			throw e;
		} catch (Exception e) {
			throw new ZooDistributedLockException(e);
		}
	}

	protected boolean doLockInQueue(OneLock oneLock) throws InterruptedException {
		boolean retry = false;
		// 不能获取锁准备等待
		queueLock.writeLock().lock();
		try {
			// 先入队列
			lockQueue.addLast(oneLock);

			Stat lockStat = new Stat();
			LockNodeData lockNodeData = getLockNodeData(lockStat);
			if (lockStat.getNumChildren() < maxConcurrent) { // 能获取锁直接获取
				lockQueue.remove(oneLock);
				retry = true;

			} else {
				// 还是不能获取锁, 等待
				boolean await = lockAwait(oneLock);
				if (!await) { // 时间耗尽
					lockQueue.remove(oneLock);
					return false;
				}
				// 时间未耗尽而得机会
				lockQueue.remove(oneLock);
				retry = true;
			}
		} finally {
			queueLock.writeLock().unlock();
		}

		if (retry) {
			// 有并发，再试
			oneLock.retryTimes(oneLock.retryTimes() + 1);
			return doLock(oneLock);
		}
		return false;
	}

	protected boolean lockAwait(OneLock oneLock)  throws InterruptedException {
		if (oneLock.timeoutInMillis() > 0) {
			long remain = oneLock.createTimeInMills() + oneLock.timeoutInMillis() - System.currentTimeMillis();
			if (remain <= 0) { // 等待超时了
				return false;
			} else {
				try {
					return queueLock.writeLock().newCondition().await(remain, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					if (oneLock.interrupted()) {
						throw e;
					} else {
						return lockAwait(oneLock);
					}
				}
			}
		} else if (oneLock.timeoutInMillis() < 0) {
			// 无限等待
			try {
				queueLock.writeLock().newCondition().await();
				return true;
			} catch (InterruptedException e) {
				if (oneLock.interrupted()) {
					throw e;
				} else {
					return lockAwait(oneLock);
				}
			}
		} else {
			return false;
		}
	}

	protected String[] parseName(String name) {
		return name.split("_", 3);
	}

	@Override
	public void unlock() {
		if (lockThreadLocal.get() == null) {
			return;
		}

	}
}
