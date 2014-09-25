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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.util.Log;
import org.mitre.svmp.protocol.SVMPProtocol;
import org.mitre.svmp.protocol.SVMPProtocol.AppsResponse;
import org.mitre.svmp.protocol.SVMPProtocol.Response;

/**
 * @author Joe Portner
 * C->S: Receives Apps Launch request messages from the client and launches apps accordingly
 * S->C: Detects when the launcher is started, then sends a message to the client to terminate the video activity
 */
public class LauncherHandler extends BaseHandler {
    private static final String TAG = LauncherHandler.class.getName();

    private Context context;
    private PackageManager packageManager;
    private boolean ignoreNextBroadcast = false;

    public LauncherHandler(BaseServer baseServer) {
        // this BroadcastReceiver requires that the sender has the LAUNCHER_STARTED_PERMISSION
        super(SVMP_BROADCAST_PERMISSION, baseServer, LAUNCHER_STARTED_ACTION);
        this.context = baseServer.getContext();
        packageManager = context.getPackageManager();
    }

    // S->C
    public void onReceive(Context context, Intent intent) {
        // validate the action of the intent
        if (LAUNCHER_STARTED_ACTION.equals(intent.getAction())) {
            if (ignoreNextBroadcast) {
                // we just opened a "Single app mode" connection and started our SVMP launcher
                // i.e. we don't want to tell the client to exit its video activity
                Log.d(TAG, "Ignoring LAUNCHER_STARTED_ACTION broadcast");
                ignoreNextBroadcast = false;
            }
            else {
                Log.d(TAG, "Receiving LAUNCHER_STARTED_ACTION broadcast");
                // build an AppList
                AppsResponse.Builder arBuilder = AppsResponse.newBuilder();
                arBuilder.setType(AppsResponse.AppsResponseType.EXIT);
                // build a Response
                Response.Builder rBuilder = Response.newBuilder();
                rBuilder.setType(Response.ResponseType.APPS);
                // add the AppList to the Response
                rBuilder.setApps(arBuilder);
                // send the Response
                sendMessage(rBuilder.build());
            }
        }
    }

    // C->S
    public void handleMessage(SVMPProtocol.AppsRequest appsRequest) {
        if (appsRequest.hasPkgName()) {
            // the client is asking us to launch a specific app
            // set the default launcher to LauncherActivity
            ignoreNextBroadcast = true; // we're about to start the SVMP launcher, ignore the next broadcast received
            setDefaultLauncher(context, packageManager, true);

            // start the requested app
            String pkgName = appsRequest.getPkgName();
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkgName);
            if (intent != null) {
                //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        }
        else {
            // the client wants to go to the normal Launcher "desktop"
            // set the default launcher to aosp launcher
            setDefaultLauncher(context, packageManager, false);
        }
    }

    // sets the preferred launcher; if svmp is true, will set to LauncherActivity, otherwise will set to aosp launcher
    protected static void setDefaultLauncher(Context context, PackageManager packageManager, boolean svmp) {
        Log.d(TAG, "Setting default launcher to: " + (svmp ? "svmp" : "aosp"));

        // set up args
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_HOME);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        int bestMatch = IntentFilter.MATCH_CATEGORY_EMPTY;
        ComponentName aospComponent = new ComponentName("com.android.launcher", "com.android.launcher2.Launcher");
        ComponentName svmpComponent = new ComponentName(context.getPackageName(), LauncherActivity.class.getName());
        ComponentName[] components = new ComponentName[] {aospComponent, svmpComponent};

        // set preferred launcher and clear preferences for other launcher
        ComponentName preferred = svmp ? svmpComponent : aospComponent,
                other = svmp ? aospComponent : svmpComponent;
        packageManager.clearPackagePreferredActivities(other.getPackageName());
        packageManager.addPreferredActivity(filter, bestMatch, components, preferred);

        // start the preferred launcher
        Intent launchIntent = new Intent(Intent.ACTION_MAIN);
        launchIntent.setComponent(preferred);
        launchIntent.addCategory(Intent.CATEGORY_HOME);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // required to start an activity from outside of an Activity context
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK); // when this is started, clear other launcher activities
        context.startActivity(launchIntent);
    }
}
