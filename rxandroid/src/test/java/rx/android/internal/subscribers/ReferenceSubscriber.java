package rx.android.internal.subscribers;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ReferenceSubscriber<T> extends NotifyingSubscriber<T> {
    private final AtomicReference<T> onNext;
    private final AtomicReference<Throwable> onError;
    private final AtomicBoolean onCompleted;
    private final AtomicInteger onNextCount = new AtomicInteger();

    public ReferenceSubscriber() {
        this(new AtomicReference<T>(), new AtomicReference<Throwable>(), new AtomicBoolean());
    }

    public ReferenceSubscriber(AtomicReference<T> onNext, AtomicReference<Throwable> onError,
            AtomicBoolean onCompleted) {
        this.onNext = onNext;
        this.onError = onError;
        this.onCompleted = onCompleted;
    }

    @Override public void onCompleted() {
        onCompleted.set(true);
    }

    @Override public void onError(Throwable e) {
        onError.set(e);
    }

    @Override public void onNext(T t) {
        onNextCount.incrementAndGet();
        onNext.set(t);
    }

    @Override protected void onUnsubscribe() {

    }

    public T getLatest() {
        return onNext.get();
    }

    public Throwable getError() {
        return onError.get();
    }

    public boolean isCompleted() {
        return onCompleted.get();
    }

    public int getOnNextCount() {
        return onNextCount.get();
    }
}
