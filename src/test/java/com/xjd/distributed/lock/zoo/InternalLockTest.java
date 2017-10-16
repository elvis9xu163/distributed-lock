package com.xjd.distributed.lock.zoo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

/**
 * @author elvis.xu
 * @since 2017-10-16 15:07
 */
public class InternalLockTest {

	public static void main(String[] args) throws InterruptedException, IOException {
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

		InternalLock lock = new InternalLock("/test2/bizlock", client);
		lock.start();

		long start = System.currentTimeMillis();
		boolean f = lock.lock("hello", 30000L, 1000L, 1, -1, true);
		System.out.println("cost: " + (System.currentTimeMillis() - start));
		System.out.println(System.currentTimeMillis());
		System.out.println(f);


		String i = null;
		BufferedReader r = new BufferedReader(new InputStreamReader(System.in, Charset.forName("utf8")));
		while ( !(i = r.readLine()).equals("-1") ) {
			System.out.println(i);
		}

		System.out.println(System.currentTimeMillis());
		lock.unlock("hello");

		lock.close();
		client.close();
	}
}