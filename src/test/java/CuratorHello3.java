import java.util.concurrent.TimeUnit;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.framework.recipes.nodes.PersistentTtlNode;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;


/**
 * @author elvis.xu
 * @since 2017-06-27 11:04
 */
public class CuratorHello3 {
	public static void main(String[] args) throws Exception {
		RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);

//		CuratorFramework client = CuratorFrameworkFactory.newClient("service4:2181,service4:2182", 5000, 3000, retryPolicy);
		CuratorFramework client = CuratorFrameworkFactory.builder()
//				.connectString("service4:2181,service4:2182")
//				.connectString("service4:2181")
				.connectString("localhost:2181")
				.connectionTimeoutMs(3000)
				.sessionTimeoutMs(5000)
				.retryPolicy(retryPolicy)
				.namespace("test")
				.build();

		client.start();

		PersistentTtlNode persistentTtlNode = new PersistentTtlNode(client, "/container", 10L, "X".getBytes());
		persistentTtlNode.start();
		System.out.println(persistentTtlNode.waitForInitialCreate(10, TimeUnit.SECONDS));
		persistentTtlNode.close();

		client.getData().usingWatcher((CuratorWatcher) watchedEvent -> {
			System.out.println(watchedEvent);
		}).forPath("/container");

		client.create().withTtl(10000L).creatingParentContainersIfNeeded().withMode(CreateMode.PERSISTENT_WITH_TTL).forPath("/container/ttlnode");

		client.getData().usingWatcher((CuratorWatcher) watchedEvent -> {
			System.out.println("node: " + watchedEvent);
		}).forPath("/container/ttlnode");

		for (int i = 0; i < 600; i++) {
			Thread.sleep(1000L);
		}
		client.close();
	}
}
