package com.xjd.distributed.lock2;

/**
 * @author elvis.xu
 * @since 2017-08-29 18:47
 */
public class DistributedLockException extends RuntimeException {
	public DistributedLockException() {
	}

	public DistributedLockException(String message) {
		super(message);
	}

	public DistributedLockException(String message, Throwable cause) {
		super(message, cause);
	}

	public DistributedLockException(Throwable cause) {
		super(cause);
	}

	public DistributedLockException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}


	public static class QueueExceedException extends DistributedLockException {

	}

	public static class LockInfoNotMatchException extends DistributedLockException {
		public LockInfoNotMatchException(String message) {
			super(message);
		}
	}


}
