package com.xjd.distributed.lock.zoo;

import org.apache.curator.framework.CuratorFramework;

import com.xjd.distributed.lock.DistributedLockException;
import com.xjd.utils.basic.AssertUtils;
import com.xjd.utils.basic.StringUtils;

/**
 * @author elvis.xu
 * @since 2017-10-16 18:11
 */
public class ZooDistributedLocker implements AutoCloseable {
	protected InternalLock internalLock;
	/** 0-未启动, 1-已启动, 2-已停止 */
	protected volatile int status = 0;

	public ZooDistributedLocker(String namespace, CuratorFramework curatorFramework) {
		AssertUtils.assertArgumentNonNull(curatorFramework, "curatorFramework can not be null.");
		namespace = StringUtils.trimToEmpty(namespace);
		if (namespace.length() > 0 && !namespace.startsWith("/")) {
			throw new IllegalArgumentException("namespace must be a valid zoo path");
		}
		internalLock = new InternalLock(namespace, curatorFramework);
	}

	public void start() {
		if (status == 2) throw new DistributedLockException.LockClosedException();
		if (status == 1) return;
		internalLock.start();
		status = 1;
	}

	@Override
	public void close() {
		if (status == 0) throw new DistributedLockException.LockNotRunningException();
		if (status == 2) return;
		internalLock.close();
		status = 2;
	}

	public ZooDistributedLock getLock(String name, long expireInMillis, int maxConcurrent, int maxQueue) {
		if (status != 1) throw new DistributedLockException.LockNotRunningException();
		AssertUtils.assertArgumentNonBlank(name, "name cannot be empty.");
		AssertUtils.assertArgumentGreaterEqualThan(maxConcurrent, 1, "maxConcurrent must > 0.");
		return new ZooDistributedLock(this, name.trim(), expireInMillis, maxConcurrent, maxQueue);
	}

	/**
	 * @param name            the identified name for the lock, so different locks should have different names.
	 * @param timeoutInMillis the timeout in milliseconds before getting the lock. {@code <0} means waiting infinitely;
	 *                        {@code 0} means trying to get the lock immediately.
	 * @param expireInMillis  the expire time in milliseconds of the lock. {@code <=0} means unlimited.
	 * @param maxConcurrent   the max number lock to have the lock in meantime. it must be positive number.
	 * @param maxQueue        the max number lock to wait in queue for the lock. {@code <0} means unlimited; {@code 0} means no queue.
	 * @param interrupted     {@code true} means interrupted.
	 * @return {@code true} if locked, {@code false} otherwise.
	 */
	protected boolean lock(String name, long timeoutInMillis, long expireInMillis, int maxConcurrent, int maxQueue, boolean interrupted) throws InterruptedException {
		if (status != 1) throw new DistributedLockException.LockNotRunningException();
		return internalLock.lock(name, timeoutInMillis, expireInMillis, maxConcurrent, maxQueue, interrupted);
	}

	protected boolean isLocked(String name) {
		if (status != 1) throw new DistributedLockException.LockNotRunningException();
		return internalLock.isLocked(name);
	}

	protected void unlock(String name) {
		internalLock.unlock(name);
	}
}
