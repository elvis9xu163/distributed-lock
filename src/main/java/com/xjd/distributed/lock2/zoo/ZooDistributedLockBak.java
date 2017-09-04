package com.xjd.distributed.lock2.zoo;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;

import com.xjd.distributed.lock2.DistributedLock;
import com.xjd.distributed.lock2.DistributedLockException;
import com.xjd.utils.basic.AssertUtils;
import com.xjd.utils.basic.JsonUtils;

/**
 * @author elvis.xu
 * @since 2017-08-29 17:16
 */
@Slf4j
public class ZooDistributedLockBak implements DistributedLock, AutoCloseable {
	protected String key;
	protected CuratorFramework curatorFramework;
	protected int maxConcurrent;
	protected int maxQueue;
	protected long maxExpireInMills;

	protected AtomicBoolean closed;

	public ZooDistributedLockBak(String key, CuratorFramework curatorFramework, int maxConcurrent, int maxQueue, long maxExpireInMills) {
		AssertUtils.assertArgumentNonBlank(key, "key must be set");
		AssertUtils.assertArgumentNonNull(curatorFramework, "curatorFramework must be set");
		AssertUtils.assertArgumentGreaterEqualThan(maxConcurrent, 1, "maxConcurrent must greater than 0");
		AssertUtils.assertArgumentGreaterEqualThan(maxQueue, 0, "maxQueue must greater than or equal 0");
		AssertUtils.assertArgumentGreaterEqualThan(maxExpireInMills, 1, "maxExpireInMills must greater than 0");
		if (!key.startsWith("/")) {
			throw new IllegalArgumentException("key must be a valid zookeeper path");
		}
		this.key = key;
		this.curatorFramework = curatorFramework;
		this.maxConcurrent = maxConcurrent;
		this.maxQueue = maxQueue;
		this.maxExpireInMills = maxExpireInMills;
		closed = new AtomicBoolean(false);

		init();
	}

	@Override
	public int getMaxConcurrent() {
		return maxConcurrent;
	}

	@Override
	public int getMaxQueue() {
		return maxQueue;
	}

	@Override
	public long getMaxExpireInMills() {
		return maxExpireInMills;
	}

	@Override
	public int getNowConcurrent() {
		return 0;
	}


	@Override
	public int getNowQueue() {
//		curatorFramework.getChildren().storingStatIn();
		return 0;
	}

	@Override
	public long getExpireInMills() {
		return 0;
	}

	@Override
	public String getKey() {
		return null;
	}

	@Override
	public boolean lockNow() {
		return false;
	}

	@Override
	public boolean lockInQueue(long time, TimeUnit timeUnit) {
		return false;
	}

	@Override
	public boolean lockInQueue() {
		return false;
	}

	@Override
	public void lock() {

	}

	@Override
	public void unlock() {

	}

	public void init() {
		LockSettings lockStat = LockSettings.builder().maxConcurrent(maxConcurrent).maxQueue(maxQueue).maxExpireInMills(maxExpireInMills).build();
		try {
			// 判断节点是否存在
			Stat stat = curatorFramework.checkExists().forPath(key);
			if (stat == null) { // 不存在创建之
				try {
					curatorFramework.create().withMode(CreateMode.PERSISTENT).forPath(key, JsonUtils.toJson(lockStat).getBytes(Charset.forName("UTF-8")));
				} catch (KeeperException.NodeExistsException e) {
					// do nothing
				}
				stat = new Stat();
			}
			byte[] bytes = curatorFramework.getData().storingStatIn(stat).forPath(key);
			LockSettings existLockStat;
			try {
				existLockStat = JsonUtils.fromJson(new String(bytes, Charset.forName("UTF-8")), LockSettings.class);
			} catch (JsonUtils.JsonException e) {
				throw new ZooDistributedLockException("path '" + key + "' is not a lock2 node.");
			}
			if (!lockStat.equals(existLockStat)) {
				throw new DistributedLockException.LockInfoNotMatchException("the info of this lock2 does not match the info of zoo lock2 path '" + key + "'");
			}
		} catch (DistributedLockException e) {
			throw e;
		} catch (Exception e) {
			throw new ZooDistributedLockException("init lock2 '" + key + "' failed.", e);
		}
	}

	@Override
	public void close() {
		if (closed.get()) {
			return;
		}
		if (!closed.compareAndSet(false, true)) {
			return;
		}
		try {
			Stat stat = curatorFramework.checkExists().forPath(key);
			if (stat == null) {
				// 已经删除
				return;
			}
			byte[] bytes;
			final boolean[] changed = new boolean[]{false};
			try {
				bytes = curatorFramework.getData().storingStatIn(stat).usingWatcher((CuratorWatcher) event -> {
					switch (event.getType()) {
					case None:
					case ChildWatchRemoved:
					case DataWatchRemoved:
						break;
					default:
						changed[0] = true;
					}
				}).forPath(key);
			} catch (KeeperException.NoNodeException e) {
				// 节点被删除
				return;
			}
			LockSettings existLockStat;
			try {
				existLockStat = JsonUtils.fromJson(new String(bytes, Charset.forName("UTF-8")), LockSettings.class);
			} catch (JsonUtils.JsonException e) {
				// 节点数据无法解析, 说明已被他人使用
				return;
			}
			LockSettings lockStat = LockSettings.builder().maxConcurrent(maxConcurrent).maxQueue(maxQueue).maxExpireInMills(maxExpireInMills).build();
			if (!lockStat.equals(existLockStat)) {
				// 信息不一致, 说明已被他人使用
				return;
			}
			if (!changed[0]) { // 此时还未修改, 此处是存在并发的，比如更新通知有延迟
				try {
					curatorFramework.delete().withVersion(stat.getVersion()).forPath(key);
				} catch (KeeperException.NoNodeException e) {
					// 节点被删除
					return;
				}
			}
		} catch (Exception e) {
			log.error("An error occurred when closing the lock2, please check it.", e);
			return;
		}
	}

	protected void assertNotClose() {

	}

//	protected int[] getNowStats() {
//
//		curatorFramework.getChildren().storingStatIn()
//		return null;
//	}

}
