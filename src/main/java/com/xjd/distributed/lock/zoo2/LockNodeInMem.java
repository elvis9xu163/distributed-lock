package com.xjd.distributed.lock.zoo2;

import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A lock node
 * @author elvis.xu
 * @since 2017-09-04 09:53
 */

@Getter
@Setter
@Accessors(chain = true)
public class LockNodeInMem {
	private String path;
	private int retryTimes = 0;

	private long createTimeInMills;
	private long waitingTime;
	private TimeUnit waitingTimeUnit;
	private boolean interrupted;
}
