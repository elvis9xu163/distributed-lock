package com.xjd.distributed.lock;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * @author elvis.xu
 * @since 2017-10-16 18:03
 */
public interface DistributedLock extends Lock, AutoCloseable {

	/**
	 * Get the lock status
	 * @return {@code true} if locked, {@code false} otherwise.
	 */
	boolean isLocked();

	@Override
	default Condition newCondition() {
		throw new UnsupportedOperationException();
	}

	@Override
	default void close() {
		unlock();
	};
}

