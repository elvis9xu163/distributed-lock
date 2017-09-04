package com.xjd.distributed.lock.zoo;

import com.xjd.distributed.lock2.DistributedLockException;

/**
 * @author elvis.xu
 * @since 2017-08-30 14:40
 */
public class ZooDistributedLockException extends DistributedLockException {
	public ZooDistributedLockException() {}

	public ZooDistributedLockException(String message) {
		super(message);
	}

	public ZooDistributedLockException(String message, Throwable cause) {
		super(message, cause);
	}

	public ZooDistributedLockException(Throwable cause) {
		super(cause);
	}

	public ZooDistributedLockException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public static class LockClosedException extends ZooDistributedLockException {
	}
	public static class LockNotRunningException extends ZooDistributedLockException {
	}
}