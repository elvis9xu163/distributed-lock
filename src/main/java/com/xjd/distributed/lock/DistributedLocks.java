package com.xjd.distributed.lock;

import java.util.HashMap;

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
	protected static HashMap<ZooDistributedLockerKey, ZooDistributedLocker> zooDistributedLockerCache = new HashMap<>();

	public static DistributedLock getLock(ZooDistributedLocker zooLocker, String id, long expireInMillis, int maxConcurrent, int maxQueue) {
		return zooLocker.getLock(id, expireInMillis, maxConcurrent, maxQueue);
	}

	public static DistributedLock getLock(CuratorFramework client, String path) {
		return new CuratorReentrantDistributedLock(client, path);
	}

	public static DistributedLock getLock(InterProcessMutex interProcessMutex) {
		return new CuratorReentrantDistributedLock(interProcessMutex);
	}

	public static DistributedReadWriteLock getReadWriteLock(CuratorFramework client, String basePath) {
		return new CuratorReentrantDistributedReadWriteLock(client, basePath);
	}

	public static DistributedReadWriteLock getReadWriteLock(InterProcessReadWriteLock interProcessReadWriteLock) {
		return new CuratorReentrantDistributedReadWriteLock(interProcessReadWriteLock);
	}


	protected static class ZooDistributedLockerKey {
		protected CuratorFramework curatorFramework;
		protected String namespace;

		public ZooDistributedLockerKey(CuratorFramework curatorFramework, String namespace) {
			this.curatorFramework = curatorFramework;
			this.namespace = namespace;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof ZooDistributedLockerKey)) return false;

			ZooDistributedLockerKey that = (ZooDistributedLockerKey) o;

			if (!curatorFramework.equals(that.curatorFramework)) return false;
			return namespace.equals(that.namespace);
		}

		@Override
		public int hashCode() {
			int result = curatorFramework.hashCode();
			result = 31 * result + namespace.hashCode();
			return result;
		}
	}
}
