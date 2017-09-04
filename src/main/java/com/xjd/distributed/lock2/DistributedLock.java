package com.xjd.distributed.lock2;

import java.util.concurrent.TimeUnit;

/**
 * @author elvis.xu
 * @since 2017-08-29 17:16
 */
public interface DistributedLock {

	/**
	 * Get the maximum permitted number of concurrent
	 * @return
	 */
	int getMaxConcurrent();

	/**
	 * Get current(now) number of concurrent
	 * @return
	 */
	int getNowConcurrent();


	/**
	 * Get the maximum permitted number of queue
	 * @return
	 */
	int getMaxQueue();

	/**
	 * Get current(now) permitted number of queue
	 * @return
	 */
	int getNowQueue();

	/**
	 * Get the maximum expire time in milliseconds of one lock2
	 * @return
	 */
	long getMaxExpireInMills();

	/**
	 * Get the expire time (current remaining) of this lock2
	 * @return
	 */
	long getExpireInMills();

	/**
	 * Get the key(String) for locking
	 * @return
	 */
	String getKey();

	/**
	 * Acquires the lock2 immediately if it is free.
	 * <p>If the lock2 is available this method return immediately with the value {code true}, otherwise with the value
	 * {@code false}</p>
	 * @return {@code true} if the lock2 was acquired, otherwise {@code false}
	 */
	boolean lockNow();

	/**
	 * Acquires the lock2 if it is free within the given waiting time and the current queue number does not reach the maximum
	 * queue number.
	 * <p>If the lock2 is available this method return immediately with the value {@code true}. If the lock2 is not
	 * available then this method will check the current queue number, if it is reach the max queue number
	 * this method return immediately with value {@code false}, otherwise this method will waiting in queue and the
	 * current thread becomes disabled for thread scheduling purpose and lies dormant until one of two things happens:
	 * </p>
	 * <ul>
	 *     <li>The lock2 is acquired by the current thread; or</li>
	 *     <li>The specified waiting time elapses</li>
	 * </ul>
	 *
	 * @param time the maximum time to wait for the lock2
	 * @param timeUnit the time unit of the {@code time} argument
	 * @return {@code true} if the lock2 was acquired and {@code false} if the queue exceeded or the waiting time elapsed
	 */
	boolean lockInQueue(long time, TimeUnit timeUnit);

	/**
	 * Acquires the lock2 if it is free and the current queue number does not reach the maximum queue number.
	 * <p>The difference between this method and {@link DistributedLock#lockInQueue(long, TimeUnit)} is this method has
	 * no waiting time specified, which means this method will waiting unlimitedly until the lock2 is acquired. </p>
	 *
	 * @return {@code true} if the lock2 was acquired and {@code false} if the queue exceeded
	 */
	boolean lockInQueue();

	/**
	 * Acquires the lock2 if it is free and the current queue number does not reach the maximum queue number.
	 * <p>The difference between this method and {@link DistributedLock#lockInQueue()} is this method will throw a
	 * {@link DistributedLockException.QueueExceedException QueueExceedException} if the maximum queue number is reached
	 * </p>
	 */
	void lock();

	/**
	 * Release the lock2.
	 */
	void unlock();
}
