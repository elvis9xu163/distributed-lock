package com.xjd.distributed.lock.zoo;

/**
 * @author elvis.xu
 * @since 2017-09-01 11:14
 */
public class ZooInternalLock {

	protected ZooDistributedLock zooDistributedLock;

	protected ZooInternalLock(ZooDistributedLock zooDistributedLock) {
		this.zooDistributedLock = zooDistributedLock;
	}
}
