package com.xjd.distributed.lock.zoo;


import com.xjd.distributed.lock.DistributedLockException;

/**
 * @author elvis.xu
 * @since 2017-10-11 15:31
 */
public class ZooDistributedLockException extends DistributedLockException {
	public ZooDistributedLockException() {
	}

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
}
