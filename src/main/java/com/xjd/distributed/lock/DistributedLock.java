package com.xjd.distributed.lock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * @author elvis.xu
 * @since 2017-08-29 17:16
 */
public interface DistributedLock extends ConcurrentableLock, AutoCloseable {
	/**
	 * Get the maximum expire time in milliseconds of one lock
	 * @return
	 */
	long getMaxExpireInMills();

	/**
	 * Get the expire time (current remaining) of this lock
	 * @return -1 means unlocked, 0 means expired, positive number means the time remaining
	 */
	long getExpireInMills();

	/**
	 * Get the lock status
	 * @return {@code true} if locked, {@code false} otherwise.
	 */
	boolean isLocked();

	@Override
	boolean tryLock(long time, TimeUnit unit);

	boolean tryLockInterruptibly(long time, TimeUnit unit) throws InterruptedException;

	@Override
	default Condition newCondition() {
		throw new UnsupportedOperationException();
	}

	/**
	 * start the lock
	 */
	void start();

	@Override
	void close();
}
