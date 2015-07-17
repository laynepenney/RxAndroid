package rx.android.internal;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.subscriptions.CompositeSubscription;

public final class Util {
    private Util() { throw new AssertionError("no instances"); }

    public static Subscription attachToComposite(Subscription subscription,
            CompositeSubscription cs) {
        Subscription sub = new ChildSubscription(subscription, cs);
        cs.add(sub);
        return sub;
    }

    public static <T> Subscription subscribeWithComposite(Observable<T> observable,
            Subscriber<? super T> subscriber,
            CompositeSubscription cs) {
        final Subscription actual = observable.subscribe(subscriber);
        final Subscription sub = attachToComposite(actual, cs);
        // NOTE: We have to do this in order to remove the subscription from the list if the
        // observable completes before the composite is unsubscribed
        subscriber.add(sub);
        return sub;
    }
}
