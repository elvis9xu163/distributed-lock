package com.xjd.distributed.lock;

import java.util.concurrent.locks.Lock;

/**
 * Lock support concurrent acquired by multi threads
 * @author elvis.xu
 * @since 2017-09-01 11:03
 */
public interface ConcurrentableLock extends Lock {
	/**
	 * Get the maximum permitted number of concurrent lock
	 * @return
	 */
	int getMaxConcurrent();

	/**
	 * Get current(now) number of concurrent lock
	 * @return
	 */
	int getNowConcurrent();
}
