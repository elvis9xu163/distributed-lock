package com.xjd.distributed.lock.zoo2;

import java.util.concurrent.TimeUnit;

import com.xjd.distributed.lock.DistributedLock;

/**
 * @author elvis.xu
 * @since 2017-09-04 09:37
 */
public class ZooDistributedLock implements DistributedLock, AutoCloseable {

	protected InternalLock internalLock;

	public ZooDistributedLock(InternalLock internalLock) {
		this.internalLock = internalLock;
	}

	protected InternalLock getInternalLock() {
		return internalLock;
	}

	@Override
	public int getMaxConcurrent() {
		return internalLock.maxConcurrent;
	}

	@Override
	public long getMaxExpireInMills() {
		return internalLock.maxExpireInMills;
	}

	@Override
	public int getNowConcurrent() {
		return internalLock.getNowConcurrent();
	}

	@Override
	public long getExpireInMills() {
		return internalLock.getExpireInMills();
	}

	@Override
	public boolean tryLock() {
		try {
			return internalLock.lock(0, TimeUnit.MILLISECONDS, false);
		} catch (InterruptedException e) {
			// impossible
			e.printStackTrace();
		}
	}

	@Override
	public boolean tryLock(long time, TimeUnit unit) {
		try {
			return internalLock.lock(time, unit, false);
		} catch (InterruptedException e) {
			// impossible
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean tryLockInterruptibly(long time, TimeUnit unit) throws InterruptedException {
		return internalLock.lock(time, unit, true);
	}

	@Override
	public void lock() {
		try {
			internalLock.lock(-1, TimeUnit.MILLISECONDS, false);
		} catch (InterruptedException e) {
			// impossible
			e.printStackTrace();
		}
	}

	@Override
	public void lockInterruptibly() throws InterruptedException {
		internalLock.lock(-1, TimeUnit.MILLISECONDS, true);
	}

	@Override
	public void unlock() {
		internalLock.unlock();
	}

	public void start() {
		internalLock.start();
	}

	@Override
	public void close() {
		internalLock.close();
	}
}
