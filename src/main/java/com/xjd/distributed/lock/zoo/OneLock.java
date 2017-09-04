package com.xjd.distributed.lock.zoo;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * @author elvis.xu
 * @since 2017-09-01 14:53
 */

@Getter
@Setter
@Accessors(fluent = true)
public class OneLock {
	private String node;

	private long createTimeInMills;
	private long timeoutInMillis;
	private boolean interrupted;
	private int retryTimes;
}
