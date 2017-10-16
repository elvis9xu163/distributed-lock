package com.xjd.distributed.lock.zoo;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.Getter;
import lombok.Setter;

/**
 * @author elvis.xu
 * @since 2017-10-12 17:54
 */
@Getter
@Setter
public class LockTree {
	protected String path;
	protected String locksPath;
	protected String lockersPath;

	protected Map<String, LockNode> lockNodeMap = new HashMap<>();


	public LockTree(String path) {
		this.path = path;
		String p = path.endsWith("/") ? path : path + "/";
		this.locksPath = p + "locks";
		this.lockersPath = p + "lockers";
	}

	@Getter
	@Setter
	public static class Node {
		protected String path;

		public Node(String path) {
			this.path = path;
		}
	}

	@Getter
	@Setter
	public static class LockNode extends Node{
		protected volatile int lockLeafCount = 0;
		protected LinkedHashMap<String, LockLeaf> lockLeafMap = new LinkedHashMap<>();

		public LockNode(String path) {
			super(path);
		}
	}

	@Getter
	@Setter
	public static class LockLeaf extends Node {
		public static final String NAME_PREFIX = "lock";

		protected int ranking = 0;
		protected LocalLock localLock;

		public LockLeaf(String path) {
			super(path);
		}
	}

	@Getter
	@Setter
	public static class LocalLock {
		protected String path;
		/** 0-Not waiting, 1-waiting lock, 2-locked, 3-unlocked, 4-cancelled, 5-expired */
		protected int status = 1; // 一创建其实已经进入队列了
		protected Lock statusLock = new ReentrantLock();
		protected CountDownLatch initLatch = new CountDownLatch(1);
		protected CountDownLatch lockLatch = new CountDownLatch(1);

		protected long expireInMillis = 0; // 过期时间
		protected Future expireTaskFuture;

	}

	@Getter
	@Setter
	public static class LocalNode {
		protected String path;
		int maxConcurrent;
		int maxQueue;

		public LocalNode(String path, int maxConcurrent, int maxQueue) {
			this.path = path;
			this.maxConcurrent = maxConcurrent;
			this.maxQueue = maxQueue;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof LocalNode)) return false;

			LocalNode localNode = (LocalNode) o;

			if (maxConcurrent != localNode.maxConcurrent) return false;
			if (maxQueue != localNode.maxQueue) return false;
			return path != null ? path.equals(localNode.path) : localNode.path == null;
		}

		@Override
		public int hashCode() {
			int result = path != null ? path.hashCode() : 0;
			result = 31 * result + maxConcurrent;
			result = 31 * result + maxQueue;
			return result;
		}

		@Override
		public String toString() {
			return "LocalNode{" +
					"path='" + path + '\'' +
					", maxConcurrent=" + maxConcurrent +
					", maxQueue=" + maxQueue +
					'}';
		}
	}
}
