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

/**
 * @author Joe Portner
 */

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class BaseLogThread extends Thread {
    private static final String TAG = BaseLogThread.class.getName();

    private String address;
    private int port;
    private String linePrefix;
    private String lineSuffix;
    private String procString;

    private DatagramSocket socket = null;
    private BufferedReader reader = null;

    public BaseLogThread(String androidID, String address, int port, String procString) {
        this.address = address;
        this.port = port;
        this.linePrefix = "";
        if (androidID.length() > 0)
            this.linePrefix = androidID + ": ";
        this.lineSuffix = System.getProperty("line.separator");
        this.procString = procString;
    }

    public void run() {
        Log.d(TAG, "Start");
        try {
            // try to create a UDP socket
            socket = new DatagramSocket();

            // create LogCat process with filters, if any
            Process process = Runtime.getRuntime().exec(procString);

            // declare variables
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String nextLine;
            String output;

            // loop, read logs from the buffer and send them to the destination
            while (!isInterrupted() && (nextLine = reader.readLine()) != null ) {
                output = linePrefix + nextLine + lineSuffix;
                DatagramPacket packet = new DatagramPacket(output.getBytes(), output.length(), InetAddress.getByName(address), port);
                try {
                    socket.send(packet);
                } catch (SocketException e) {
                    Log.e(TAG, "ERROR: " + e.getMessage());
                }
                /*if ( isInterrupted() ) {
                    Log.d( TAG, "interrupted." );
                    break;
                }*/
            }
        } catch (SocketException e) {
            Log.e(TAG, "Socket creation failed: " + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.getMessage());
        } finally {
            Log.d( TAG, "Stop" );
        }
    }

    public void stopThread() {
        this.interrupt();
        if (socket != null)
            socket.close();
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
