package com.xjd.distributed.lock;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.InterProcessReadWriteLock;

import com.xjd.distributed.lock.curator.CuratorReentrantDistributedLock;
import com.xjd.distributed.lock.curator.CuratorReentrantDistributedReadWriteLock;
import com.xjd.distributed.lock.zoo.ZooDistributedLocker;

/**
 * @author elvis.xu
 * @since 2017-11-02 19:23
 */
public abstract class DistributedLocks {

	public static DistributedLock getConcurrentLock(ZooDistributedLocker zooLocker, String id, long expireInMillis, int maxConcurrent, int maxQueue) {
		return zooLocker.getLock(id, expireInMillis, maxConcurrent, maxQueue);
	}

	public static DistributedLock getMutexLock(CuratorFramework client, String path) {
		return new CuratorReentrantDistributedLock(client, path);
	}

	public static DistributedLock getMutexLock(InterProcessMutex interProcessMutex) {
		return new CuratorReentrantDistributedLock(interProcessMutex);
	}

	public static DistributedReadWriteLock getReadWriteLock(CuratorFramework client, String basePath) {
		return new CuratorReentrantDistributedReadWriteLock(client, basePath);
	}

	public static DistributedReadWriteLock getReadWriteLock(InterProcessReadWriteLock interProcessReadWriteLock) {
		return new CuratorReentrantDistributedReadWriteLock(interProcessReadWriteLock);
	}

}
