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

import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.RemoteException;
import android.util.Log;
import android.view.InputDevice;
import org.mitre.svmp.protocol.SVMPProtocol;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Joe Portner
 * C->S: Receives Config request messages from the client and injects them into the system
 * Requires platform-level access to run properly (uses hidden APIs and platform permissions)
 */
public class ConfigHandler implements Constants {
    private static final String TAG = ConfigHandler.class.getName();

    private static final String PREF_CURRENTID = "confighandler-currentid";

    private Context context;
    private IActivityManager activityManager;

    public ConfigHandler(Context context) {
        this.context = context;
        activityManager = ActivityManagerNative.getDefault();
    }

    public void handleConfig(SVMPProtocol.Config msg) {
        if (msg.hasHardKeyboard()) {
            handleKeyboardChange(msg.getHardKeyboard());
        }
    }

    public void handleKeyboardChange(boolean hardKeyboardAttached) {
        int[] deviceIds = InputDevice.getDeviceIds();
        List<InputDevice> devices = new ArrayList<InputDevice>();
        for (int i : deviceIds)
            devices.add(InputDevice.getDevice(i));

        boolean virtualKeyboardExists = false;
        for (InputDevice device : devices) {
            if (device.getName().equals("SVMP USB Keyboard")) {
                virtualKeyboardExists = true;
                break;
            }
        }

        if (hardKeyboardAttached && !virtualKeyboardExists) {
            // find an ID for the new virtual keyboard
            SharedPreferences prefs = context.getSharedPreferences("svmp-prefs", 0);
            int id = prefs.getInt(PREF_CURRENTID, 0);
            if (id > 0)
                id += 2;
            else
                id = getNextDeviceId(deviceIds);
            // store the virtual keyboard ID we just obtained
            applyCurrentId(context, id);

            // instruct the InputManagerService to create and attack two new virtual keyboards
            Intent intent = new Intent(KEYBOARD_ATTACHED_ACTION);
            intent.putExtra("id", id); // the ID to create a keyboard for
            context.sendBroadcast(intent); // no need to protect from snoopers, this broadcast is harmless
            updateConfig(true);
        }
        else if (!hardKeyboardAttached && virtualKeyboardExists) {
            // instruct the InputManagerService to remove existing virtual keyboards
            Intent intent = new Intent(KEYBOARD_DETACHED_ACTION);
            context.sendBroadcast(intent); // no need to protect from snoopers, this broadcast is harmless
            updateConfig(false);
        }


    }

    // only called on BOOT_COMPLETED
    public static void onBoot(Context context) {
        // reset the current virtual keyboard ID value to zero
        applyCurrentId(context, 0);
    }

    // applies the current virtual keyboard ID value to the shared preferences for future use
    private static void applyCurrentId(Context context, int id) {
        SharedPreferences.Editor prefEditor = context.getSharedPreferences("svmp-prefs", 0).edit();
        prefEditor.putInt(PREF_CURRENTID, id);
        prefEditor.apply();
    }

    private int getNextDeviceId(int[] deviceIds) {
        int maxId = 0;
        for (int i : deviceIds) {
            if (i > maxId)
                maxId = i;
        }
        return maxId + 1;
    }

    // refreshes the current Configuration
    private void updateConfig(boolean hardKeyboardAttached) {
        // create the appropriate Configuration
        Configuration config = context.getResources().getConfiguration();
        config.keyboard = hardKeyboardAttached ? Configuration.KEYBOARD_QWERTY : Configuration.KEYBOARD_NOKEYS;
        config.hardKeyboardHidden = hardKeyboardAttached ? Configuration.HARDKEYBOARDHIDDEN_NO : Configuration.HARDKEYBOARDHIDDEN_YES;

        // update the system Configuration
        try {
            activityManager.updateConfiguration(config);
        } catch(RemoteException e) {
            Log.e(TAG, "updateConfig failed: " + e.getMessage());
        }
    }
}