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

import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.util.Log;

import org.mitre.svmp.protocol.SVMPProtocol;
import org.mitre.svmp.protocol.SVMPProtocol.*;
import org.mitre.svmp.protocol.SVMPProtocol.Response.ResponseType;
import org.mitre.svmp.protocol.SVMPSensorEventMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;

/**
 * Base, 1 socket at a time, TCP Server.
 * 
 * Threaded to not completely block and ignore new connections while an
 * existing one is running, but a new connection will kick off any previous
 * logins. Only one live connection at a time is allowed.
 */
public abstract class BaseServer implements Constants {
    private ServerSocket proxySocket;
    private BlockingQueue<Socket> clientSocketQueue;
    private OutputStream proxyOut = null;
    private InputStream proxyIn = null;
    private int proxyPort;
    private static final String TAG = BaseServer.class.getName();
    
    private native int InitSockClient(String path);
    private native int SockClientWrite(int fd,SVMPSensorEventMessage event);
    private native int SockClientClose(int fd); 
    private int sockfd;

    private Context context;
    private LocationHandler locationHandler;
    private IntentHandler intentHandler;
    private NotificationHandler notificationHandler;
    private KeyHandler keyHandler;
    private ConfigHandler configHandler;
    private LauncherHandler launcherHandler;
    private ExecutorService sensorMsgExecutor;
    private final Object sendMessageLock = new Object();
    private WebrtcHandler webrtcHandler = null;

    public BaseServer(Context context) throws IOException {
        this.context = context;
        sockfd = InitSockClient("/dev/socket/svmp_sensors");
        Log.d(TAG, "InitSockClient returned " + sockfd);
        this.proxyPort = PROXY_PORT;
    }
    
    static {
    	System.loadLibrary("remote_events_jni");
    }

    public void start() throws IOException {
        // start receiving location broadcast messages
        locationHandler = new LocationHandler(this);

        // start receiving intent intercept messages
        intentHandler = new IntentHandler(this);

        // start receiving notification intercept messages
        notificationHandler = new NotificationHandler(this);

        // receives KeyEvent request messages from the client and injects them into the system
        keyHandler = new KeyHandler();

        // receives Config request messages from the client and injects them into the system
        configHandler = new ConfigHandler(context);

        // receives Apps Launch messages from the client
        // receives launcher broadcasts and sends Apps Exit messages to the client
        launcherHandler = new LauncherHandler(this);

        // We create a SingleThreadExecutor because it executes sequentially
        // this guarantees that sensor event messages will be sent in order
        sensorMsgExecutor = Executors.newSingleThreadExecutor();

        this.proxySocket = new ServerSocket(proxyPort);
        Log.d(TAG, "Event server listening on proxyPort " + proxyPort);

        clientSocketQueue = new SynchronousQueue<Socket>();
        new Thread(new SocketAcceptor()).start();
        this.run();
    }

    private class SocketAcceptor implements Runnable {
        public void run() {
            InputStream oldClientIn = null;
            OutputStream oldClientOut = null;

            while (true) {
                try {
                    Socket socket = proxySocket.accept();
                    Log.i(TAG, "New client socket connection received.");

                    // If there's already an existing connection, kill it.
                    if (proxyIn != null) {
                        Log.i(TAG, "Previous client session still active, disconnecting it.");
                        // close the streams to break anything blocked reading them
                        if (proxyIn != null)  proxyIn.close();
                        if (proxyOut != null) proxyOut.close();
                    }

                    proxyOut = null;
                    proxyIn = null;
                    clientSocketQueue.put(socket);
                } catch (IOException e) {
                    Log.e(TAG, "Problem accepting socket: " + e.getMessage());
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while handing off client socket. " + e.getMessage());
                }
            }
        }
    }

    protected void run() {
        while (true) {
            try {
                clientHandler(clientSocketQueue.take());
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for client socket. " + e.getMessage());
            }
        }
    }

    private void clientHandler(Socket socket) {
        Log.d(TAG, "Client connection handler starting.");
        try {
            proxyIn = socket.getInputStream();
            proxyOut = socket.getOutputStream();
            while (socket.isConnected()) {
                SVMPProtocol.Request msg = SVMPProtocol.Request.parseDelimitedFrom(proxyIn);
                //logInfo("Received message " + msg.getType().name());

                if( msg == null )
                    break;

                switch(msg.getType()) {
                case SCREENINFO:
                    handleScreenInfo(msg);
                    break;
                case TOUCHEVENT:
                    handleTouch(msg.getTouchList());
                    break;
                case SENSOREVENT:
                    // use the thread pool to handle this
                    handleSensor(msg.getSensorList());
                    break;
                case INTENT:
                    intentHandler.handleMessage(msg);
                    break;
                case LOCATION:
                    locationHandler.handleMessage(msg);
                    break;
                case VIDEO_PARAMS:
                    initWebRTC(msg);
                    webrtcHandler.sendMessage(Response.newBuilder()
                        .setType(ResponseType.VMREADY).build());
                    break;
                case WEBRTC:
                    webrtcHandler.handleMessage(msg);
                    break;
                case ROTATION_INFO:
                    handleRotationInfo(msg);
                    break;
                case PING:
                    handlePing(msg);
                    break;
                case TIMEZONE:
                    handleTimezone(msg);
                    break;
                case APPS:
                    handleApps(msg);
                    break;
                case KEYEVENT:
                    keyHandler.handleKeyEvent(msg.getKey());
                    break;
                case CONFIG:
                    configHandler.handleConfig(msg.getConfig());
                    break;
                default:
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error on socket: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (webrtcHandler != null) {
                webrtcHandler.disconnectAndExit();
            }
 
            try {
                proxyIn.close();
                proxyOut.close();
                socket.close();
            } catch (Exception e) {
                // Don't care
            } finally {
                proxyIn = null;
                proxyOut = null;
                socket = null;
            }
            Log.d(TAG, "Client connection handler finished.");
        }
    }

    private void initWebRTC(SVMPProtocol.Request msg) {
        // only ever create one WebRTC handler
        if (webrtcHandler == null) {
            Log.d(TAG, "Creating new WebRTC Handler.");
            // the video parameters won't change from one session to the next
            webrtcHandler = new WebrtcHandler(BaseServer.this, msg.getVideoInfo(), context);
        } else {
            Log.d(TAG, "Reusing existing WebRTC Handler.");
        }
    }
 
    protected void sendMessage(Response message) {
        // use synchronized statement to ensure only one message gets sent at a time
        synchronized(sendMessageLock) {
            try {
                message.writeDelimitedTo(proxyOut);
            } catch (IOException e) {
                Log.e(TAG, "Error sending message to client: " + e.getMessage());
            }
        }
    }

    protected Context getContext() {
        return context;
    }

    public abstract void handleScreenInfo(final Request message) throws IOException;
    public abstract void handleTouch(final List<TouchEvent> event);

    private void handleSensor(final List<SensorEvent> eventList) {
        // we can receive a batch of sensor events; process each event individually
        for (SensorEvent event : eventList)
            // this SensorEvent was sent from the client, let's pass it on to the Sensor Message Unix socket
            sensorMsgExecutor.execute(new SensorMessageRunnable(this, sockfd, event));
    }

    public void handleRotationInfo(final Request request) {
        RotationInfo rotationInfo = request.getRotationInfo();
        int rotation = rotationInfo.getRotation(); // get rotation value from protobuf
        Intent intent = new Intent(ROTATION_CHANGED_ACTION); // set action
        intent.putExtra("rotation", rotation); // add rotation value to intent
        context.sendBroadcast(intent); // send broadcast
        // the receiver is protected by requiring the sender to have the SVMP_BROADCAST permission
    }

    // when we receive a Ping message from the client, pack it back up in a Response wrapper and return it
    public void handlePing(final Request request) throws IOException {
        if (request.hasPingRequest() ) {
            // get the ping message that was sent from the client
            Ping ping = request.getPingRequest();

            // pack the ping message in a Response wrapper
            Response.Builder builder = Response.newBuilder();
            builder.setType(ResponseType.PING);
            builder.setPingResponse(ping);
            Response response = builder.build();

            sendMessage(response);
        }
    }

    // set the system default timezone based on the client's timezone
    public void handleTimezone(final Request request) {
      if (request.hasTimezoneId()) {
        
        // Reference: packages/apps/Settings/src/com/android/settings/ZonePicker.java
        // Update the system timezone value
        final AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarm.setTimeZone(request.getTimezoneId());
      }
    }

    private void handleApps(final Request request) {
        AppsRequest appsRequest = request.getApps();
        if (appsRequest.getType() == AppsRequest.AppsRequestType.REFRESH) {
            // the client is asking for a refreshed list of available apps, handle the request
            new AppsRefreshHandler(this, appsRequest).start();
        } else if (appsRequest.getType() == AppsRequest.AppsRequestType.LAUNCH) {
            launcherHandler.handleMessage(appsRequest);
        }
    }

    // called from the SensorMessageRunnable
    public void sendSensorEvent(int sockfd, SVMPSensorEventMessage message) {
        // send message
        SockClientWrite(sockfd, message);
    }

}
