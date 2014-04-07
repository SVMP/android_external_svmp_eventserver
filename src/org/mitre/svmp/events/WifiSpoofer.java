package org.mitre.svmp.events;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.UserHandle;

/**
 * @author Joe Portner
 * Used to spoof the system into thinking that a WiFi connection is active
 * Fixes DNS issues, also allows apps that require WiFi to function (ex: DownloadManager)
 * Requires that the application is signed with the platform signature to send protected broadcasts
 * Derived from: frameworks/base/wifi/java/android/net/wifi/WifiStateMachine.java #1642
 */
public class WifiSpoofer {
    public static void doSpoof(Context context) {
        Intent intent = new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        // only receivers that were registered before boot (i.e. system services) can receive this broadcast
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(WifiManager.EXTRA_NETWORK_INFO, getNetworkInfo());
        // don't worry about LinkProperties
        // don't worry about BSSID
        // don't worry about WifiInfo

        // send a sticky broadcast that runs as all user handles
        context.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    // creates a NetworkInfo object to send in the Intent broadcast
    private static NetworkInfo getNetworkInfo() {
        NetworkInfo value = new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0, "WIFI", "");
        value.setIsAvailable(true);
        value.setDetailedState(NetworkInfo.DetailedState.CONNECTED, null, "SVMP-WiFi");
        return value;
    }
}
