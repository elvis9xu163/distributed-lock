package com.xjd.distributed.lock.zoo;

import java.util.concurrent.TimeUnit;

import com.xjd.distributed.lock.DistributedLock;
import com.xjd.distributed.lock.DistributedLockException;

/**
 * @author elvis.xu
 * @since 2017-10-16 18:12
 */
public class ZooDistributedLock implements DistributedLock {
	protected ZooDistributedLocker distributedLocker;
	protected String name;
	protected long expireInMillis;
	protected int maxConcurrent;
	protected int maxQueue;

	protected ZooDistributedLock(ZooDistributedLocker distributedLocker, String name, long expireInMillis, int maxConcurrent, int maxQueue) {
		this.distributedLocker = distributedLocker;
		this.name = name;
		this.expireInMillis = expireInMillis;
		this.maxConcurrent = maxConcurrent;
		this.maxQueue = maxQueue;
	}

	@Override
	public boolean isLocked() {
		return distributedLocker.isLocked(name);
	}

	@Override
	public void lock() {
		try {
			if (!distributedLocker.lock(name, -1L, expireInMillis, maxConcurrent, maxQueue, false)) {
				throw new DistributedLockException("cannot get distributed lock, maybe the lock queue is full");
			}
		} catch (InterruptedException e) {
			// impossible
			e.printStackTrace();
		}
	}

	@Override
	public void lockInterruptibly() throws InterruptedException {
		if (!distributedLocker.lock(name, -1L, expireInMillis, maxConcurrent, maxQueue, true)) {
			throw new DistributedLockException("cannot get distributed lock, maybe the lock queue is full");
		}
	}

	@Override
	public boolean tryLock() {
		try {
			return distributedLocker.lock(name, 0L, expireInMillis, maxConcurrent, maxQueue, false);
		} catch (InterruptedException e) {
			// impossible
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
		return distributedLocker.lock(name, unit.toMillis(time), expireInMillis, maxConcurrent, maxQueue, true);
	}

	@Override
	public void unlock() {
		distributedLocker.unlock(name);
	}
}
