import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.retry.ExponentialBackoffRetry;

import com.xjd.utils.basic.JsonUtils;


/**
 * @author elvis.xu
 * @since 2017-06-27 11:04
 */
public class CuratorHello5 {
	public static void main(String[] args) throws Exception {
		RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);

//		CuratorFramework client = CuratorFrameworkFactory.newClient("service4:2181,service4:2182", 5000, 3000, retryPolicy);
		CuratorFramework client = CuratorFrameworkFactory.builder()
//				.connectString("service4:2181,service4:2182")
//				.connectString("service4:2181")
				.connectString("127.0.0.1:2181")
				.connectionTimeoutMs(3000)
				.sessionTimeoutMs(5000)
				.retryPolicy(retryPolicy)
//				.namespace("test")
				.build();

		client.start();

//		Stat stat = client.checkExists().forPath("/test2");
//		System.out.println(JsonUtils.toJson(stat));
//
//		stat = client.checkExists().forPath("/test1");
//		System.out.println(JsonUtils.toJson(stat));

//		client.create().forPath("/test3", "H".getBytes(Charset.forName("utf8")));

//		Stat stat = new Stat();
//		byte[] bytes = client.getData().storingStatIn(stat).forPath("/test3");

//		client.delete().forPath("/test3");
//
//		List<String> list = client.getChildren().forPath("/test2");
//		for (String s : list) {
//			System.out.println(s);
//		}

//		Stat stat = new Stat();
//		List<String> list = client.getChildren().storingStatIn(stat).usingWatcher((CuratorWatcher) event -> {
////			System.out.println(event.getType());
////			List<String> list1 = client.getChildren().storingStatIn(stat).forPath("/test5");
////			System.out.println(JsonUtils.toJson(stat));
////			for (String s : list1) {
////				System.out.println(s);
////			}
//		}).forPath("/test3");
//
//		System.out.println(JsonUtils.toJson(stat));
//		for (String s : list) {
//			System.out.println(s);
//		}

//		Stat stat = new Stat();
//		List<String> childrenNames = client.getChildren().storingStatIn(stat).forPath("/test2");
//		Collections.sort(childrenNames);
//		for (String childrenName : childrenNames) {
//			System.out.println(childrenName);
//		}

		TreeCache treeCache = new TreeCache(client, "/test");
		treeCache.start();
		treeCache.getListenable().addListener((client1, event) -> {
			System.out.println(JsonUtils.toJson(event));
		});


//		System.out.println(client.create().withMode(CreateMode.PERSISTENT_SEQUENTIAL).forPath("/test3"));

		for (int i = 0; i < 600; i++) {
			Thread.sleep(1000L);
		}
		treeCache.close();
		client.close();
	}
}
