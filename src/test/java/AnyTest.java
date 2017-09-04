/**
 * @author elvis.xu
 * @since 2017-09-01 14:11
 */
public class AnyTest {
	public static void main(String[] args) throws InterruptedException {

		Thread t = new Thread(){
			@Override
			public void run() {
				System.out.println("before wait");

				synchronized (this) {
					try {
						this.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}

				System.out.println("after wait");

			}
		};

		t.start();

		Thread.sleep(2000L);

		System.out.println("notify");
		synchronized (t) {
			t.notifyAll();
		}

		t.join();


	}

}
