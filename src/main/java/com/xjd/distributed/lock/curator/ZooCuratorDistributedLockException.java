package com.xjd.distributed.lock.curator;


import com.xjd.distributed.lock.DistributedLockException;

/**
 * @author elvis.xu
 * @since 2017-10-11 15:31
 */
public class ZooCuratorDistributedLockException extends DistributedLockException {
	public ZooCuratorDistributedLockException() {
	}

	public ZooCuratorDistributedLockException(String message) {
		super(message);
	}

	public ZooCuratorDistributedLockException(String message, Throwable cause) {
		super(message, cause);
	}

	public ZooCuratorDistributedLockException(Throwable cause) {
		super(cause);
	}

	public ZooCuratorDistributedLockException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
