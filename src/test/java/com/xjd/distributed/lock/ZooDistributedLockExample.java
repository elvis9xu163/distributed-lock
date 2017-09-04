package com.xjd.distributed.lock;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

import org.apache.curator.framework.CuratorFramework;

import com.xjd.distributed.lock.zoo.ZooDistributedLock;

/**
 * @author elvis.xu
 * @since 2017-09-04 18:03
 */
public class ZooDistributedLockExample {
	public static void main(String[] args) throws InterruptedException {
		CuratorFramework curatorFramework = CuratorUtils.startCuratorFramework();

//		synchronizeLock(curatorFramework);
//		maxConcurrentLock(curatorFramework);
		frequencyLimitLock(curatorFramework);

		curatorFramework.close();
	}

	/**
	 * lock for synchronize logic
	 */
	public static void synchronizeLock(CuratorFramework curatorFramework) throws InterruptedException {
		ZooDistributedLock distributedLock = DistributedLocks.newZooDistributedLock(getLocalHost(), "/lock/syncLock1", curatorFramework, 1, 10000L, null);
		distributedLock.start();

		Thread t1 = new Thread(() -> {
			distributedLock.lock();
			System.out.println("t1 got lock");
			try {
				Thread.sleep(2000L);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println("t1 all synchronized");
			distributedLock.unlock();
		});
		Thread t2 = new Thread(() -> {
			try {
				Thread.sleep(200L);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println("t2 waiting lock");
			distributedLock.lock();
			System.out.println("t2 got lock");
			distributedLock.unlock();
		});
		t1.start();
		t2.start();
		t1.join();
		t2.join();
		distributedLock.close();
	}

	public static String getLocalHost() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * lock for limit the maximum concurrent logic
	 */
	public static void maxConcurrentLock(CuratorFramework curatorFramework) throws InterruptedException {
		ZooDistributedLock distributedLock = DistributedLocks.newZooDistributedLock(getLocalHost(), "/lock/concurrentLock1", curatorFramework, 2, 10000L, null);
		distributedLock.start();

		System.out.println("limit the concurrent to 2 and others waiting to execute.");
		List<Thread> list = new LinkedList<>();
		for (int i = 0; i < 10; i++) {
			int z = i;
			Thread t = new Thread(() -> {
				distributedLock.lock(); // waiting until the concurrent less than 2;
				System.out.println("locked: " + z);
				try {
					Thread.sleep(1000L);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.out.println("unlocking: " + z);
				distributedLock.unlock();
			});
			list.add(t);
			t.start();
		}

		for (Thread t : list) {
			t.join();
		}

		list.clear();
		System.out.println("limit the concurrent to 2 and others return immediately.");
		for (int i = 0; i < 10; i++) {
			int z = i;
			Thread t = new Thread(() -> {
				boolean locked = distributedLock.tryLock();
				if (locked) {
					System.out.println("locked: " + z);
					try {
						Thread.sleep(10L);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					System.out.println("unlocking: " + z);
					distributedLock.unlock();
				} else {
					System.out.println("not locked: " + z);
				}
			});
			list.add(t);
			t.start();
		}

		for (Thread t : list) {
			t.join();
		}

		distributedLock.close();
	}

	/**
	 * In a unit interval limit the frequency(max concurrent)
	 * @param curatorFramework
	 * @throws InterruptedException
	 */
	public static void frequencyLimitLock(CuratorFramework curatorFramework) throws InterruptedException {
		// 2 concurrent in every 10 seconds
		ZooDistributedLock distributedLock = DistributedLocks.newZooDistributedLock(getLocalHost(), "/lock/concurrentLock1", curatorFramework, 2, 10000L, null);
		distributedLock.start();

		System.out.println("limit the concurrent to 2 and others waiting to execute.");
		List<Thread> list = new LinkedList<>();
		for (int i = 0; i < 10; i++) {
			int z = i;
			Thread t = new Thread(() -> {
//				distributedLock.lock(); // waiting until the concurrent less than 2;
//				System.out.println("locked: " + z);

				boolean b = distributedLock.tryLock(); // return immediately
				if (b) {
					System.out.println("locked: " + z);
				} else {
					System.out.println("not lock: " + z);
				}

				// do not call unlock as the lock expire automaticly
//				distributedLock.unlock();
			});
			list.add(t);
			t.start();
		}
		for (Thread t : list) {
			t.join();
		}
		distributedLock.close();
	}

}
