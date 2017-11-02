package com.xjd.distributed.lock.curator;

import java.util.concurrent.locks.Lock;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessReadWriteLock;

import com.xjd.distributed.lock.DistributedReadWriteLock;

/**
 * @author elvis.xu
 * @since 2017-11-02 19:47
 */
public class CuratorReentrantDistributedReadWriteLock implements DistributedReadWriteLock {
	protected InterProcessReadWriteLock interProcessReadWriteLock;
	protected CuratorReentrantDistributedLock readLock, writeLock;

	public CuratorReentrantDistributedReadWriteLock(CuratorFramework client, String basePath) {
		this(new InterProcessReadWriteLock(client, basePath));
	}

	public CuratorReentrantDistributedReadWriteLock(CuratorFramework client, String basePath, byte[] lockData) {
		this(new InterProcessReadWriteLock(client, basePath, lockData));
	}

	public CuratorReentrantDistributedReadWriteLock(InterProcessReadWriteLock interProcessReadWriteLock) {
		this.interProcessReadWriteLock = interProcessReadWriteLock;
		readLock = new CuratorReentrantDistributedLock(interProcessReadWriteLock.readLock());
		writeLock = new CuratorReentrantDistributedLock(interProcessReadWriteLock.writeLock());
	}

	@Override
	public Lock readLock() {
		return readLock;
	}

	@Override
	public Lock writeLock() {
		return writeLock;
	}
}
