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

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.Log;
import com.google.protobuf.ByteString;
import org.mitre.svmp.protocol.SVMPProtocol.AppInfo;
import org.mitre.svmp.protocol.SVMPProtocol.AppsRequest;
import org.mitre.svmp.protocol.SVMPProtocol.AppsResponse;
import org.mitre.svmp.protocol.SVMPProtocol.Response;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Joe Portner
 * C->S: Receives Apps Refresh requests from client, polls installed apps, and responds
 */
public class AppsRefreshHandler extends Thread {
    private static final String TAG = AppsRefreshHandler.class.getName();

    private BaseServer baseServer;
    private AppsRequest appsRequest;

    public AppsRefreshHandler(BaseServer baseServer, AppsRequest appsRequest) {
        this.baseServer = baseServer;
        this.appsRequest = appsRequest;
    }

    @Override
    public void run() {
        PackageManager pm = baseServer.getContext().getPackageManager();
        // get a list of installed apps.
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);

        int screenDensity = getScreenDensity();
        // create a map of app data from the list of installed apps
        Map<String, Tuple> appDataMap = new TreeMap<String, Tuple>();
        for (ResolveInfo resolveInfo : resolveInfos) {
            String pkgName = resolveInfo.activityInfo.packageName;
            String appName = resolveInfo.loadLabel(pm).toString();
            Drawable drawable = null;
            try {
                Resources resources = pm.getResourcesForApplication(pkgName);
                if (resources != null) {
                    int iconId = resolveInfo.getIconResource();
                    if (iconId != 0)
                        drawable = resources.getDrawableForDensity(iconId, screenDensity);
                }
//                if (drawable == null)
//                    drawable = pm.getApplicationIcon(pkgName);
            } catch (Resources.NotFoundException e) {
                Log.e(TAG, "Failed to find application icon for package '" + pkgName + "':" + e.getMessage());
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Failed to find application info for package '" + pkgName + "':" + e.getMessage());
            }
            byte[] icon = Utility.drawableToBytes(drawable);
            Tuple tuple = new Tuple(pkgName, appName, icon);

            // add the tuple to the map
            appDataMap.put(pkgName, tuple);
        }

        // build an AppList
        AppsResponse.Builder arBuilder = AppsResponse.newBuilder();
        arBuilder.setType(AppsResponse.AppsResponseType.REFRESH);

        // get Request info
        List<AppInfo> currentApps = appsRequest.getCurrentList();
        for (AppInfo appInfo : currentApps) {
            String pkgName = appInfo.getPkgName();
            // compare the client's current apps to our appDataMap
            if (appDataMap.containsKey(pkgName)) {
                // we have this package installed, check to see if it's different from the client's records
                Tuple tuple = appDataMap.remove(pkgName);
                boolean different = false;

                String appName = appInfo.getAppName();
                byte[] iconHash = null;
                if (appInfo.hasIconHash())
                    iconHash = appInfo.getIconHash().toByteArray();
                if (!appName.equals(tuple.appName)) {
                    different = true;
                }
                else {
                    byte[] tupleIconHash = getIconHash(tuple);
                    if (!Arrays.equals(iconHash, tupleIconHash)) {
                        different = true;
                    }
                }

                if (different) {
                    // we have this package installed, but it's different, instruct the client to update it
                    arBuilder.addUpdated(buildAppInfo(tuple));
                }
                // otherwise, the package hasn't changed, don't tell the client to do anything to it
            }
            else {
                // we don't have this package installed, instruct the client to remove it
                arBuilder.addRemoved(pkgName);
            }
        }

        // for any remaining apps, instruct the client to add it
        for (Tuple tuple : appDataMap.values()) {
            arBuilder.addNew(buildAppInfo(tuple));
        }

        // build a Response
        Response.Builder rBuilder = Response.newBuilder();
        rBuilder.setType(Response.ResponseType.APPS);
        // add the AppList to the Response
        rBuilder.setApps(arBuilder);

        baseServer.sendMessage(rBuilder.build());
    }

    // finds the target screen density to get icons for
    private int getScreenDensity() {
        // see Android reference: http://developer.android.com/reference/android/util/DisplayMetrics.html
        int value = appsRequest.getScreenDensity();

        // apps should not target intermediate densities, instead relying on scaling up/down from standard densities
        switch(appsRequest.getScreenDensity()) {
            case DisplayMetrics.DENSITY_TV:
                value = DisplayMetrics.DENSITY_HIGH;
                break;
            case 400: // api 19: DisplayMetrics.DENSITY_400
            case DisplayMetrics.DENSITY_XXHIGH:
            case 640: // api 18: DisplayMetrics.DENSITY_XXXHIGH
                value = DisplayMetrics.DENSITY_XHIGH;
                break;
            // all other densities are standard densities, let the value remain
        }
        return value;
    }

    // converts an AppInfo's icon into a 20-byte hash
    private byte[] getIconHash(Tuple tuple) {
        byte[] value = null;
        byte[] icon = tuple.icon;

        if (icon != null) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                value = md.digest(icon);
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "getIconHash failed: " + e.getMessage());
            }
        }

        return value;
    }

    private AppInfo.Builder buildAppInfo(Tuple tuple) {
        AppInfo.Builder aiBuilder = AppInfo.newBuilder();
        aiBuilder.setPkgName(tuple.pkgName);
        aiBuilder.setAppName(tuple.appName);
        if (tuple.icon != null)
            aiBuilder.setIcon(ByteString.copyFrom(tuple.icon));
        return aiBuilder;
    }

    private class Tuple {
        private String pkgName;
        private String appName;
        private byte[] icon;

        private Tuple(String pkgName, String appName, byte[] icon) {
            this.pkgName = pkgName;
            this.appName = appName;
            if (this.appName == null)
                this.appName = pkgName;
            this.icon = icon;
        }
    }
}
