package com.xjd.distributed.lock.zoo;

import java.util.concurrent.ScheduledExecutorService;

import lombok.Setter;
import lombok.experimental.Accessors;

import org.apache.curator.framework.CuratorFramework;

/**
 * @author elvis.xu
 * @since 2017-09-04 09:43
 */

@Setter
@Accessors(fluent = true)
public class ZooDistributedLockBuilder {
	protected String namespace;
	protected String path;
	protected CuratorFramework curatorFramework;
	protected int maxConcurrent;
	protected long maxExpireInMills;
	protected ScheduledExecutorService scheduledExecutorService;

	public ZooDistributedLock build() {
		InternalLock internalLock = new InternalLock(namespace, path, curatorFramework, maxConcurrent, maxExpireInMills, scheduledExecutorService);
		return new ZooDistributedLock(internalLock);
	}
}
