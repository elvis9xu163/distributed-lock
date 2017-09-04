package com.xjd.distributed.lock;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.curator.framework.CuratorFramework;

import com.xjd.distributed.lock.zoo.ZooDistributedLock;
import com.xjd.distributed.lock.zoo.ZooDistributedLockBuilder;

/**
 * @author elvis.xu
 * @since 2017-09-04 14:27
 */
public class DistributedLocks {

	private DistributedLocks() {
	}

	public static ZooDistributedLockBuilder zooBuilder() {
		return new ZooDistributedLockBuilder();
	}

	public static ZooDistributedLock newZooDistributedLock(String path, CuratorFramework curatorFramework, long maxExpireInMills) {
		return newZooDistributedLock(null, path, curatorFramework, 1, maxExpireInMills, null);
	}

	public static ZooDistributedLock newZooDistributedLock(String namespace, String path, CuratorFramework curatorFramework, int maxConcurrent, long maxExpireInMills, ScheduledExecutorService scheduledExecutorService) {
		return zooBuilder()
				.namespace(namespace)
				.path(path)
				.curatorFramework(curatorFramework)
				.maxConcurrent(maxConcurrent)
				.maxExpireInMills(maxExpireInMills)
				.scheduledExecutorService(scheduledExecutorService)
				.build();
	}
}
