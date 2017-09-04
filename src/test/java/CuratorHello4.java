import java.nio.charset.Charset;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;


/**
 * @author elvis.xu
 * @since 2017-06-27 11:04
 */
public class CuratorHello4 {
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

//
//		List<CuratorTransactionResult> curatorTransactionResults = client.transaction().forOperations(
//				client.transactionOp().delete().forPath("/test1"),
//				client.transactionOp().create().forPath("/test3"),
//				client.transactionOp().delete().forPath("/test3"),
//				client.transactionOp().delete().forPath("/test3"));
//		for (CuratorTransactionResult curatorTransactionResult : curatorTransactionResults) {
//			System.out.println(JsonUtils.toJson(curatorTransactionResult));
//		}

		client.setData().withVersion(0).forPath("/test2", "HELLO".getBytes(Charset.forName("UTF-8")));

		for (int i = 0; i < 600; i++) {
			Thread.sleep(1000L);
		}
		client.close();
	}
}
