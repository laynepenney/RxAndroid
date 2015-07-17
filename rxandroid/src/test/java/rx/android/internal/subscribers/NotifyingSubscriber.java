package rx.android.internal.subscribers;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import rx.Subscriber;
import rx.Subscription;
import rx.android.subscribers.ActionSubscriber;
import rx.functions.Action0;
import rx.functions.Action1;

public abstract class NotifyingSubscriber<T> extends Subscriber<T> {

    protected NotifyingSubscriber() {
        this(null);
    }

    protected NotifyingSubscriber(Subscriber<?> op) {
        super(op);
        add(new NotifyingSubscription(this));
    }

    protected abstract void onUnsubscribe();

    public static <T> NotifyingSubscriber<T> create(Action1<? super T> onNext,
            Action0 onUnsubscribe) {
        return create(new ActionSubscriber<T>(onNext), onUnsubscribe);
    }

    public static <T> NotifyingSubscriber<T> create(Action1<? super T> onNext,
            Action1<Throwable> onError,
            Action0 onUnsubscribe) {
        return create(new ActionSubscriber<T>(onNext, onError), onUnsubscribe);
    }

    public static <T> NotifyingSubscriber<T> create(Action1<? super T> onNext,
            Action1<Throwable> onError,
            Action0 onCompleted,
            Action0 onUnsubscribe) {
        return create(new ActionSubscriber<T>(onNext, onError, onCompleted), onUnsubscribe);
    }

    public static <T> NotifyingSubscriber<T> create(final Subscriber<? super T> actual,
            final Action0 onUnsubscribe) {
        return new NotifyingSubscriber<T>(actual) {
            @Override
            protected void onUnsubscribe() {
                onUnsubscribe.call();
            }

            @Override
            public void onCompleted() {
                actual.onCompleted();
            }

            @Override
            public void onError(Throwable e) {
                actual.onError(e);
            }

            @Override
            public void onNext(T t) {
                actual.onNext(t);
            }
        };
    }

    private static final class NotifyingSubscription implements Subscription {
        private static final AtomicIntegerFieldUpdater<NotifyingSubscription> UNSUBSCRIBED_UPDATER
                = AtomicIntegerFieldUpdater.newUpdater(NotifyingSubscription.class, "unsubscribed");

        private final NotifyingSubscriber<?> parent;
        private volatile int unsubscribed;

        private NotifyingSubscription(NotifyingSubscriber<?> parent) {
            this.parent = parent;
        }

        @Override
        public void unsubscribe() {
            if (UNSUBSCRIBED_UPDATER.compareAndSet(this, 0, 1)) {
                parent.onUnsubscribe();
            }
        }

        @Override
        public boolean isUnsubscribed() {
            return unsubscribed != 0;
        }
    }
}
