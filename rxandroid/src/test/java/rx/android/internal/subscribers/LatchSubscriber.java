package rx.android.internal.subscribers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class LatchSubscriber<T> extends ReferenceSubscriber<T> {
    private final CountDownLatch latch = new CountDownLatch(1);

    public LatchSubscriber() { }

    public LatchSubscriber(AtomicReference<T> onNext, AtomicReference<Throwable> onError,
            AtomicBoolean onCompleted) {
        super(onNext, onError, onCompleted);
    }

    @Override public void onCompleted() {
        super.onCompleted();
        latch.countDown();
    }

    @Override public void onError(Throwable e) {
        super.onError(e);
        latch.countDown();
    }

    @Override protected void onUnsubscribe() {
        super.onUnsubscribe();
        latch.countDown();
    }

    public CountDownLatch getLatch() {
        return latch;
    }

    public void await() throws InterruptedException {
        latch.await();
    }

    public void await(long timeout, TimeUnit unit) throws InterruptedException {
        latch.await(timeout, unit);
    }
}
