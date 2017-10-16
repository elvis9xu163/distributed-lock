package com.xjd.distributed.lock.zoo;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

/**
 * @author elvis.xu
 * @since 2017-10-16 19:06
 */
public class ZooDistributedLockExample {

	public static void main(String[] args) {
		RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);

//		CuratorFramework client = CuratorFrameworkFactory.newClient("service4:2181,service4:2182", 5000, 3000, retryPolicy);
		CuratorFramework client = CuratorFrameworkFactory.builder()
				.connectString("service4:2181,service4:2182")
				.connectionTimeoutMs(3000)
				.sessionTimeoutMs(5000)
				.retryPolicy(retryPolicy)
//				.namespace("curator")
				.build();

		client.start();

		ZooDistributedLocker zooDistributedLocker = new ZooDistributedLocker("/test2/locker3", client);
		zooDistributedLocker.start();

		for (int i = 0; i < 10; i++) {


			{
				ZooDistributedLock hello1 = zooDistributedLocker.getLock("hello1", 5000L, 1, -1);
				long start = System.currentTimeMillis();
				hello1.lock();
				System.out.println("cost: " + (System.currentTimeMillis() - start));
				hello1.unlock();
			}
			{
				ZooDistributedLock hello1 = zooDistributedLocker.getLock("hello2", 5000L, 1, -1);
				long start = System.currentTimeMillis();
				hello1.lock();
				System.out.println("cost: " + (System.currentTimeMillis() - start));
				hello1.unlock();
			}
		}


		zooDistributedLocker.close();

		client.close();
	}
}