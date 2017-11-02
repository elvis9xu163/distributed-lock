package com.xjd.distributed.lock.zoo;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import com.xjd.distributed.lock.DistributedLock;
import com.xjd.distributed.lock.DistributedLocks;
import com.xjd.distributed.lock.DistributedReadWriteLock;
import com.xjd.utils.basic.LockUtils;

/**
 * @author elvis.xu
 * @since 2017-11-02 20:07
 */
public class DistributedLocksExample {
	public static void main(String[] args) {
		RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
		CuratorFramework client = CuratorFrameworkFactory.builder()
				.connectString("service4:2181,service4:2181")
				.connectionTimeoutMs(3000)
				.sessionTimeoutMs(5000)
				.retryPolicy(retryPolicy)
				.build();
		client.start();

		{
			DistributedLock lock = DistributedLocks.getMutexLock(client, "/lockPath");
			try (LockUtils.LockResource lr = LockUtils.lock(lock)) {
				System.out.println("AAA");
			}
		}
		{
			DistributedReadWriteLock lock = DistributedLocks.getReadWriteLock(client, "/lockPath");
			try (LockUtils.LockResource lr = LockUtils.lock(lock.writeLock())) {
				System.out.println("BBB WRITE");
			}
			try (LockUtils.LockResource lr = LockUtils.lock(lock.readLock())) {
				System.out.println("BBB READ");
			}
		}
		ZooDistributedLocker zooDistributedLocker = new ZooDistributedLocker("/lockPath2", client);
		zooDistributedLocker.start();
		{
			DistributedLock lock = DistributedLocks.getConcurrentLock(zooDistributedLocker, "ALOCK", 60000L, 1, 100);
			try (LockUtils.LockResource lr = LockUtils.lock(lock)) {
				System.out.println("CCC");
			}
		}
		zooDistributedLocker.close();
		client.close();
	}
}
