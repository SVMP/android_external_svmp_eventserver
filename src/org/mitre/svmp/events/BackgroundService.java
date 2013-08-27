/*
 Copyright 2013 The MITRE Corporation, All Rights Reserved.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this work except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.mitre.svmp.events;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;

/**
 * @author Joe Portner
 */
public class BackgroundService extends Service implements Constants {
    private static final String TAG = BackgroundService.class.getName();

    Thread myThread;
    MyRunnable myRunnable;

    @Override
    public void onCreate() {
        myRunnable = new MyRunnable();
        myThread = new Thread(myRunnable);
        myThread.setDaemon(true); // important, otherwise JVM does not exit at end of main()
        myThread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Received start command");

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // TODO: interrupt the socket loop so it shuts down gracefully, then the thread can end
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    class MyRunnable implements Runnable {
        private EventServer eventServer;
        public void run() {
            try {
                eventServer = new EventServer(getApplicationContext());
            } catch( IOException e ) {
                Log.e(TAG, e.getMessage());
            }
        }
    }
}
