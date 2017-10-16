package com.xjd.distributed.lock;

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

	public static class ConfigNotMatchException extends DistributedLockException {
		public ConfigNotMatchException(String message) {
			super(message);
		}
	}

	public static class LockClosedException extends DistributedLockException {
	}

	public static class LockNotRunningException extends DistributedLockException {
	}

}
