package com.xjd.distributed.lock.zoo2;

/**
 * @author elvis.xu
 * @since 2017-09-04 09:43
 */
public class ZooDistributedLockBuilder {

	public ZooDistributedLockBuilder key(String key) {
		return this;
	}

	public ZooDistributedLock build() {
		InternalLock internalLock = new InternalLock();

		return new ZooDistributedLock(internalLock);
	}
}
