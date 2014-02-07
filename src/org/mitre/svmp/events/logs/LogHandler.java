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
package org.mitre.svmp.events.logs;

import android.content.Context;
import android.provider.Settings.Secure;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author Joe Portner
 */
public class LogHandler {
    private static final String TAG = LogHandler.class.getName();

    private Context context;
    private Properties properties;
    LogCatThread logCatThread;

    public LogHandler(Context context) {
        this.context = context;
        properties = new Properties();

        try {
            InputStream inputStream = context.getAssets().open("config.properties");
            properties.load(inputStream);
        } catch (IOException e) {
            Log.e(TAG, "Couldn't load properties file: " + e.getMessage());
        }
    }

    public void startForwarding() {
        String androidID = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);

        if (properties.getProperty("EnableLogCatForwarding", "false").equals("true")) {
            Log.d(TAG, "Starting LogCat forwarding...");
            String address = properties.getProperty("LogCatDestAddress");
            String portString = properties.getProperty("LogCatDestPort");
            String format = properties.getProperty("LogCatFormat", "threadtime");
            String filter = properties.getProperty("LogCatFilter", "");

            int port = 0;
            try {
                port = Integer.parseInt(portString);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Couldn't parse LogCat port: " + e.getMessage());
            }

            if (address == null)
                Log.e(TAG, "Couldn't read LogCat address");
            else if (port < 1 || port > 65535)
                Log.e(TAG, "Invalid LogCat port");
            else {
                logCatThread = new LogCatThread(androidID, address, port, format, filter);
                logCatThread.start();
            }
        }
    }

    public void stopForwarding() {
        if (logCatThread != null)
            logCatThread.stopThread();
    }
}
