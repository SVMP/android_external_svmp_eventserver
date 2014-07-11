/*
Copyright 2014 The MITRE Corporation, All Rights Reserved.

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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * @author Joe Portner
 * SVMP launcher used for "single app" mode; when the activity starts, notify SVMPD so it can act accordingly
 */
public class LauncherActivity extends Activity implements Constants {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.launcheractivity);
    }

    @Override
    public void onResume() {
        super.onResume();
        // whenever the launcher resumes, send a broadcast to the LauncherHandler
        notifyHandler();
    }

    @Override
    public void onBackPressed() {
        // the user should never see this activity, but if they do, they can press the Back button to exit
        notifyHandler();
    }

    private void notifyHandler() {
        Log.d(LauncherActivity.class.getName(), "Sending LAUNCHER_STARTED_ACTION broadcast");
        Intent intent = new Intent(LAUNCHER_STARTED_ACTION);
        sendBroadcast(intent, SVMP_BROADCAST_PERMISSION);
    }
}