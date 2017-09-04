import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author elvis.xu
 * @since 2017-09-01 14:11
 */
public class AnyTest2 {
	public static void main(String[] args) throws InterruptedException {

		Lock lock = new ReentrantLock();
		Condition condition = lock.newCondition();

		Thread t = new Thread(){
			@Override
			public void run() {
				System.out.println("before wait");

				lock.lock();
				try {
					condition.await();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				lock.unlock();

				System.out.println("after wait");

			}
		};

		t.start();

		Thread.sleep(2000L);

		lock.lock();
		System.out.println("notify");
		condition.signalAll();
		lock.unlock();

		t.join();


	}

}
