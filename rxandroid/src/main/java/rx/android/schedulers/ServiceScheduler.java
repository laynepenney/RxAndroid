/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rx.android.schedulers;


import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action0;
import rx.plugins.RxJavaPlugins;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;
import rx.subscriptions.Subscriptions;

/**
 * Rx scheduler that wraps another scheduler and runs a service while tasks are executing.
 *
 * @author Layne Penney
 */
public final class ServiceScheduler extends Scheduler {
    private static final String LOG_TAG = "ServiceScheduler";
    private static final Subscription EMPTY_SUBSCRIPTION = Subscriptions.empty();
    private static final long DEFAULT_STOP_DELAY_TIME = 5;
    private static final TimeUnit DEFAULT_STOP_DELAY_UNIT = TimeUnit.SECONDS;

    private final Context context;
    private final Class<? extends Service> serviceClass;
    private final Scheduler scheduler;
    private final long stopDelayTime;
    private final TimeUnit stopDelayUnit;
    private final AtomicInteger wip = new AtomicInteger();
    private final AtomicBoolean pendingStop = new AtomicBoolean();
    private final StopAction stopAction = new StopAction();
    private volatile long lastZeroWip;

    private ServiceScheduler(Context context, Class<? extends Service> serviceClass, Scheduler scheduler,
            long stopDelayTime, TimeUnit stopDelayUnit) {
        this.context = context.getApplicationContext();
        this.serviceClass = serviceClass;
        this.scheduler = scheduler;
        this.stopDelayTime = stopDelayTime;
        this.stopDelayUnit = stopDelayUnit;
    }

    /**
     * Creates a ServiceScheduler that runs a specified service while tasks are executing.
     *
     * @param context
     *         any context
     * @param serviceClass
     *         the service class to run
     * @param scheduler
     *         the execution scheduler
     *
     * @return the ServiceScheduler
     *
     * @see {@link #create(android.content.Context, Class, rx.Scheduler, long, java.util.concurrent.TimeUnit)}
     */
    public static ServiceScheduler create(Context context, Class<? extends Service> serviceClass, Scheduler scheduler) {
        return create(context, serviceClass, scheduler, DEFAULT_STOP_DELAY_TIME, DEFAULT_STOP_DELAY_UNIT);
    }

    /**
     * Creates a ServiceScheduler that runs a specified service while tasks are executing.
     *
     * @param context
     *         any context
     * @param serviceClass
     *         the service class to run
     * @param scheduler
     *         the execution scheduler
     * @param stopDelayTime
     *         the time to wait after all work is done before stopping the service
     * @param stopDelayUnit
     *         the unit for the stopDelayTime
     *
     * @return the ServiceScheduler
     */
    public static ServiceScheduler create(Context context, Class<? extends Service> serviceClass, Scheduler scheduler,
            long stopDelayTime, TimeUnit stopDelayUnit) {
        return new ServiceScheduler(context, serviceClass, scheduler, stopDelayTime, stopDelayUnit);
    }

    @Override
    public Worker createWorker() {
        return new ServiceWorker(this, scheduler.createWorker());
    }

    private void incrementAction() {
        final int wip = this.wip.getAndIncrement();
        if (wip == 0 && !pendingStop.get()) {
            context.startService(new Intent(context, serviceClass));
            log("starting service %s", serviceClass);
        }
        log("incremented to %d wip for service %s", wip + 1, serviceClass);
    }

    private void decrementAction() {
        final int wip = this.wip.decrementAndGet();
        if (wip == 0) {
            lastZeroWip = System.nanoTime();
            scheduleStop(stopDelayTime, stopDelayUnit);
        }
        log("decremented to %d wip for service %s", wip, serviceClass);
    }

    private void scheduleStop(long delayTime, TimeUnit delayUnit) {
        if (pendingStop.compareAndSet(false, true)) {
            // Allow delay to determine if we are still at zero before stopping
            Schedulers.computation().createWorker()
                    .schedule(stopAction, delayTime, delayUnit);
        }
    }

    private static void log(String message, Object... args) {
        if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
            Log.v(LOG_TAG, String.format(Locale.US, message, args));
        }
    }

    private class StopAction implements Action0 {
        @Override
        public void call() {
            if (pendingStop.compareAndSet(true, false) && wip.get() == 0) {
                final long elapsed = System.nanoTime() - lastZeroWip;
                final long delayNanos = stopDelayUnit.toNanos(stopDelayTime);
                if (elapsed >= delayNanos) {
                    context.stopService(new Intent(context, serviceClass));
                    log("stopping service %s", serviceClass);
                } else {
                    scheduleStop(delayNanos - elapsed, TimeUnit.NANOSECONDS);
                }
            }
        }
    }

    private static class ServiceWorker extends Worker {
        private final ServiceScheduler scheduler;
        private final Worker worker;
        private final CompositeSubscription tasks = new CompositeSubscription();

        private ServiceWorker(ServiceScheduler scheduler, Worker worker) {
            this.scheduler = scheduler;
            this.worker = worker;
        }

        @Override
        public Subscription schedule(Action0 action) {
            scheduler.incrementAction();
            final ServiceAction serviceAction = new ServiceAction(this, action, tasks);
            final Subscription sub = worker.schedule(serviceAction);
            return subscribe(serviceAction, sub);
        }

        @Override
        public Subscription schedule(Action0 action, long delayTime, TimeUnit unit) {
            scheduler.incrementAction();
            final ServiceAction serviceAction = new ServiceAction(this, action, tasks);
            final Subscription sub = worker.schedule(serviceAction, delayTime, unit);
            return subscribe(serviceAction, sub);
        }

        @Override
        public void unsubscribe() {
            worker.unsubscribe();
            tasks.unsubscribe();
        }

        @Override
        public boolean isUnsubscribed() {
            return worker.isUnsubscribed();
        }

        private Subscription subscribe(ServiceAction action, Subscription sub) {
            if (EMPTY_SUBSCRIPTION.equals(sub)) {
                scheduler.decrementAction();
                return sub;
            }
            tasks.add(action);
            return Subscriptions.from(action, sub);
        }

        private void decrementAction() {
            scheduler.decrementAction();
        }
    }

    private static final class ServiceAction implements Action0, Subscription {
        private static final AtomicIntegerFieldUpdater<ServiceAction> UNSUBSCRIBED_UPDATER
                = AtomicIntegerFieldUpdater.newUpdater(ServiceAction.class, "unsubscribed");
        private final ServiceWorker worker;
        private final Action0 actual;
        private final CompositeSubscription parent;
        private volatile int unsubscribed;

        public ServiceAction(ServiceWorker worker, Action0 actual, CompositeSubscription parent) {
            this.worker = worker;
            this.actual = actual;
            this.parent = parent;
        }

        @Override
        public void call() {
            if (isUnsubscribed()) {
                return;
            }
            try {
                actual.call();
            } catch (Throwable t) {
                RxJavaPlugins.getInstance().getErrorHandler().handleError(t);
            } finally {
                unsubscribe();
            }
        }

        @Override
        public boolean isUnsubscribed() {
            return unsubscribed != 0;
        }

        @Override
        public void unsubscribe() {
            if (UNSUBSCRIBED_UPDATER.compareAndSet(this, 0, 1)) {
                parent.remove(this);
                worker.decrementAction();
            }
        }
    }
}
