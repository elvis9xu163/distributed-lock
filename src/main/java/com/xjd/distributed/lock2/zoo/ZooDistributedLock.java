package com.xjd.distributed.lock2.zoo;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import org.apache.curator.framework.CuratorFramework;
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
public class ZooDistributedLock implements DistributedLock {
	protected String key;
	protected CuratorFramework curatorFramework;
	protected int maxConcurrent;
	protected int maxQueue;
	protected long maxExpireInMills;

	public ZooDistributedLock(String key, CuratorFramework curatorFramework, int maxConcurrent, int maxQueue, long maxExpireInMills) {
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
		int[] nowStats = getNowStats();
		return nowStats[1];
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

	protected void init() {
		LockSettings lockSettings = LockSettings.builder().maxConcurrent(maxConcurrent).maxQueue(maxQueue).maxExpireInMills(maxExpireInMills).build();
		try {
			// 判断节点是否存在
			Stat stat = curatorFramework.checkExists().forPath(key);
			if (stat == null) { // 不存在创建之
				try {
					curatorFramework.create().withMode(CreateMode.PERSISTENT).forPath(key, JsonUtils.toJson(lockSettings).getBytes(Charset.forName("UTF-8")));
				} catch (KeeperException.NodeExistsException e) {
					// do nothing
				}
				stat = new Stat();
			}
			byte[] bytes = curatorFramework.getData().storingStatIn(stat).forPath(key);
			LockSettings existLockSettings;
			try {
				existLockSettings = JsonUtils.fromJson(new String(bytes, Charset.forName("UTF-8")), LockSettings.class);
			} catch (JsonUtils.JsonException e) {
				throw new ZooDistributedLockException("path '" + key + "' is not a lock2 node.");
			}
			if (!lockSettings.equals(existLockSettings)) {
				throw new DistributedLockException.LockInfoNotMatchException("the settings of this lock2 do not match the settings of zoo lock2 path '" + key + "'");
			}
		} catch (DistributedLockException e) {
			throw e;
		} catch (Exception e) {
			throw new ZooDistributedLockException("init lock2 '" + key + "' failed.", e);
		}
	}

	protected int[] getNowStats() {
		try {
			long nowMillis = System.currentTimeMillis(); // 以统一当前时间作为参照

			Stat stat = new Stat();
			List<String> childrenNames = curatorFramework.getChildren().storingStatIn(stat).forPath(key);
			Collections.sort(childrenNames); // 将子node名排序(node名格式为: [timestamp]:[其它])

			int[] nowStats = new int[3]; // 用于存储当前状态: 0-total, 1-concurrent, 2-queue
			boolean expired = true; // 用于标识是否找到第一个未过期的节点
			String firstNonExpireNodeName = null;
			for (String nodeName : childrenNames) {
				if (!expired) {
					nowStats[0] ++;
				}
				if (!expired(nodeName, nowMillis)) {
					firstNonExpireNodeName = nodeName;
					nowStats[0] ++;
					expired = false;
				}
			}

			nowStats[1] = Math.min(nowStats[0], maxConcurrent);
			nowStats[2] = nowStats[0] - nowStats[1];
			return nowStats;

		} catch (Exception e) {
			throw new ZooDistributedLockException(e);
		}
	}

	protected String[] parseName(String name) {
		return name.split(":", 2);
	}

	protected boolean expired(String nodeName, long referTime) {
		String[] parseNames = parseName(nodeName);
		long nodeTime = Long.parseLong(parseNames[0]);

		if (nodeTime <= (System.currentTimeMillis() - this.maxExpireInMills)) {
			remove(nodeName);
			return true;
		}

		return false;
	}

	protected void remove(String nodeName) {

	}

}
