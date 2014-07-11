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
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;
import org.mitre.svmp.events.logs.LogHandler;

import java.io.IOException;

/**
 * @author Joe Portner
 */
public class BackgroundService extends Service implements Constants {
    private static final String TAG = BackgroundService.class.getName();

    Thread myThread;
    LogHandler logHandler;

    @Override
    public void onCreate() {
        super.onCreate();

        // start log forwarding
        logHandler = new LogHandler(this);
        logHandler.startForwarding();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Received start command");

        // before we do anything, check to see if location subscriptions need to be cleared out of the database
        // for some reason, we can't check to make sure the action matches BOOT_COMPLETED, the intent action is always null
        // however, if the intent isn't null, that means it's not a scheduled restart for a service crash
        if (intent != null) {
            Log.d(TAG, "Initial service start, clearing out location subscription database");
            DatabaseHandler handler = new DatabaseHandler(this);
            handler.deleteAllSubscriptions();
            handler.close();

            // clear out config preferences
            ConfigHandler.onBoot(this);

            // send a sticky broadcast that spoofs the system into thinking a WiFi connection is active
            WifiSpoofer.doSpoof(this);
        }

        // if the service isn't running, start it
        if (myThread == null) {
            myThread = new Thread(new MyRunnable());
            myThread.setDaemon(true); // important, otherwise JVM does not exit at end of main()
            myThread.start();
        }

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        logHandler.stopForwarding();
        super.onDestroy();
        // TODO: interrupt the socket loop so it shuts down gracefully, then the thread can end
    }

    @Override
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
