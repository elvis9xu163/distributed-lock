package com.xjd.distributed.lock.zoo;

import java.util.Random;

import org.apache.curator.framework.CuratorFramework;

import com.xjd.distributed.lock.CuratorUtils;
import com.xjd.distributed.lock.DistributedLocks;

/**
 * @author elvis.xu
 * @since 2017-09-04 14:40
 */
public class ZooDistributedLockExample2 {

	public static void main(String[] args) throws InterruptedException {
		CuratorFramework curatorFramework = CuratorUtils.startCuratorFramework();

		ZooDistributedLock distributedLock = DistributedLocks.newZooDistributedLock("lock:" + (new Random().nextInt(10)), "/lock/lock1", curatorFramework, 2, 10000L, null);
		distributedLock.start();

		for (int i = 1; i < 7; i++) {
			new MyThread("T" + i, distributedLock).start();
		}


		for (int i = 0; i < 20; i++) {
			Thread.sleep(1000L);
		}

		System.out.println("closing...");
		distributedLock.close();

		curatorFramework.close();
		System.out.println("closed");
	}

	public static class MyThread extends Thread {
		private String name;
		private ZooDistributedLock lock;

		public MyThread(String name, ZooDistributedLock lock) {
			this.name = name;
			this.lock = lock;
		}

		public void run() {
			System.out.println(name + " locking...");
			lock.lock();
			System.out.println(name + " locked");

			try {
				Thread.sleep((new Random().nextInt(5) + 3) * 1000L);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			System.out.println(name + " unlocking");
			lock.unlock();
			System.out.println(name + " unlocked");

		}

	}
}