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


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ServiceController;

import java.util.concurrent.TimeUnit;

import rx.Scheduler;
import rx.functions.Action0;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ServiceSchedulerTest {

    @Before
    public void setup() {
    }

    @After
    public void teardown() {
        TestService.sListener = null;
    }

    @Test
    public void shouldBeAbleToRunTestService() {
        ServiceController<TestService> serviceController = Robolectric.buildService(TestService.class);

        // assert not started
        TestService service = serviceController.attach().get();
        Assert.assertFalse(service.isRunning());

        // assert create starts the service
        service = serviceController.create().get();
        Assert.assertTrue(service.isRunning());

        // assert destroy stops the service
        service = serviceController.destroy().get();
        Assert.assertFalse(service.isRunning());
    }

    @Test
    public void shouldStartAndStopServiceWhenScheduled() throws InterruptedException {
        final Scheduler immediate = spy(new MockScheduler());
        final Scheduler.Worker immediateWorker = immediate.createWorker();
        // create mock immediate worker
        when(immediate.createWorker()).thenReturn(immediateWorker);

        final Action0 action = mock(Action0.class);
        final Context context = spy(Robolectric.setupActivity(Activity.class));
        // return mock when ServiceScheduler stores application context
        when(context.getApplicationContext()).thenReturn(context);

        ServiceScheduler scheduler = ServiceScheduler.create(context, TestService.class, immediate, 0L,
                TimeUnit.SECONDS);
        Scheduler.Worker inner = scheduler.createWorker();
        inner.schedule(action);

        // verify that the context was called by the service to start and stop
        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(context).startService(intent.capture());

        // verify that the intent was TestService.class
        Assert.assertEquals("rx.android.schedulers.ServiceSchedulerTest$TestService",
                intent.getValue().getComponent().getClassName());

        // verify that real scheduler was called
        verify(immediate, times(2)).createWorker();

        // verify that real worker was called
        ArgumentCaptor<Action0> actionCaptor = ArgumentCaptor.forClass(Action0.class);
        verify(immediateWorker).schedule(actionCaptor.capture());

        // call action and verify
        actionCaptor.getValue().call();
        verify(action).call();

        // Make sure service is stopped
        verify(context).stopService(intent.capture());
    }


    static class TestService extends Service {
        static ServiceListener sListener;
        static boolean sRunning;
        boolean running;

        @Override
        public void onCreate() {
            super.onCreate();
            sRunning = running = true;
            if (sListener != null) {
                sListener.runningChanged(true);
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            sRunning = running = false;
            if (sListener != null) {
                sListener.runningChanged(false);
            }
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        public boolean isRunning() {
            return running;
        }
    }


    static interface ServiceListener {
        void runningChanged(boolean isRunning);
    }


    static class MockScheduler extends Scheduler {
        @Override
        public Worker createWorker() {
            return mock(Worker.class);
        }
    }
}
