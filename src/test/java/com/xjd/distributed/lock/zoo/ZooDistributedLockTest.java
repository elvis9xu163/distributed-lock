package com.xjd.distributed.lock.zoo;

import org.apache.curator.framework.CuratorFramework;

import com.xjd.distributed.lock.CuratorUtils;
import com.xjd.distributed.lock.DistributedLocks;

/**
 * @author elvis.xu
 * @since 2017-09-04 14:40
 */
public class ZooDistributedLockTest {

	public static void main(String[] args) throws InterruptedException {
		CuratorFramework curatorFramework = CuratorUtils.startCuratorFramework();

		ZooDistributedLock distributedLock = DistributedLocks.newZooDistributedLock("/lock/lock1", curatorFramework, 10000L);
		distributedLock.start();


		distributedLock.lock();
		System.out.println("lock success");
		for (int i = 0; i < 20; i++) {
			Thread.sleep(1000L);
			System.out.println(i + ": " + distributedLock.isLocked());
		}

		distributedLock.unlock();
		System.out.println("unlock success");

		for (int i = 0; i < 5; i++) {
			Thread.sleep(1000L);
		}

		distributedLock.close();

		curatorFramework.close();
	}
}