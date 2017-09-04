package com.xjd.distributed.lock2.zoo;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.junit.Test;

/**
 * @author elvis.xu
 * @since 2017-08-30 15:41
 */
public class ZooDistributedLockTest {

	@Test
	public void test() throws InterruptedException {
		RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);

		CuratorFramework client = CuratorFrameworkFactory.builder()
				.connectString("localhost:2181")
				.connectionTimeoutMs(3000)
				.sessionTimeoutMs(5000)
				.retryPolicy(retryPolicy)
				.namespace("test")
				.build();
		client.start();

		ZooDistributedLock lock = new ZooDistributedLock("/lock1", client, 1, 0, 10000L);


		for (int i = 0; i < 20; i++) {
			Thread.sleep(1000L);
		}

		client.close();
	}
}