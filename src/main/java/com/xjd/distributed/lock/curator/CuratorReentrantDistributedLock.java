package com.xjd.distributed.lock.curator;

import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.LockInternalsDriver;

import com.xjd.distributed.lock.DistributedLock;

/**
 * @author elvis.xu
 * @since 2017-11-02 19:27
 */
public class CuratorReentrantDistributedLock implements DistributedLock {
	protected InterProcessMutex interProcessMutex;

	public CuratorReentrantDistributedLock(CuratorFramework client, String path) {
		interProcessMutex = new InterProcessMutex(client, path);
	}

	public CuratorReentrantDistributedLock(CuratorFramework client, String path, LockInternalsDriver driver) {
		interProcessMutex = new InterProcessMutex(client, path);
	}

	public CuratorReentrantDistributedLock(InterProcessMutex interProcessMutex) {
		this.interProcessMutex = interProcessMutex;
	}

	@Override
	public boolean isLocked() {
		return interProcessMutex.isOwnedByCurrentThread();
	}

	@Override
	public void lock() {
		while (true) {
			try {
				lockInterruptibly();
				break;
			} catch (InterruptedException e) {
				// do-nothing
			}
		}
	}

	@Override
	public void lockInterruptibly() throws InterruptedException {
		try {
			interProcessMutex.acquire();
		} catch (InterruptedException e) {
			throw e;
		} catch (Exception e) {
			throw new ZooCuratorDistributedLockException(e);
		}
	}

	@Override
	public boolean tryLock() {
		try {
			return tryLock(0, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			// do-nothing
		}
		return false;
	}

	@Override
	public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
		try {
			return interProcessMutex.acquire(time, unit);
		} catch (InterruptedException e) {
			throw e;
		} catch (Exception e) {
			throw new ZooCuratorDistributedLockException(e);
		}
	}

	@Override
	public void unlock() {
		try {
			interProcessMutex.release();
		} catch (Exception e) {
			throw new ZooCuratorDistributedLockException(e);
		}
	}
}
