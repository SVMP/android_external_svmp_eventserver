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

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import com.google.protobuf.ByteString;
import org.mitre.svmp.protocol.SVMPProtocol;
import org.mitre.svmp.protocol.SVMPProtocol.*;
import org.mitre.svmp.protocol.SVMPProtocol.Request.RequestType;
import org.mitre.svmp.protocol.SVMPProtocol.Response.ResponseType;
import org.mitre.svmp.protocol.SVMPProtocol.WebRTCMessage.WebRTCType;
import org.mitre.svmp.protocol.SVMPSensorEventMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Base, single-threaded, 1 socket at a time, TCP Server.  The server uses a queue to process
 * messages.
 * @author Dave Bryson
 */
public abstract class BaseServer implements Constants {
    private ServerSocket proxySocket;
    private OutputStream proxyOut = null;
    private InputStream proxyIn = null;
    private int proxyPort;
    private static final int BUFFER_SIZE = 8 * 1024;
    private static final String TAG = BaseServer.class.getName();
    
    private native int InitSockClient(String path);
    private native int SockClientWrite(int fd,SVMPSensorEventMessage event);
    private native int SockClientClose(int fd); 
    private int sockfd;

    private Context context;
    private LocationHandler locationHandler;
    private IntentHandler intentHandler;
    private NotificationHandler notificationHandler;
    private ExecutorService sensorMsgExecutor;
    private final Object sendMessageLock = new Object();

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
        
        // We create a SingleThreadExecutor because it executes sequentially
        // this guarantees that sensor event messages will be sent in order
        sensorMsgExecutor = Executors.newSingleThreadExecutor();

        this.proxySocket = new ServerSocket(proxyPort);
        Log.d(TAG, "Event server listening on proxyPort " + proxyPort);

        // Now that we're done booting, send a broadcast to start helper services
        Intent intent = new Intent();
        intent.setAction("org.mitre.svmp.action.BOOT_COMPLETED");
        // only BroadcastReceivers with the appropriate permission can receive this broadcast
        context.sendBroadcast(intent, "org.mitre.svmp.permission.RECEIVE_BOOT_COMPLETED");

        this.run();
    }

    protected void run() {
        while (true) {
            Socket socket = null;
            try {
                socket = proxySocket.accept();
                Log.d(TAG, "Socket connected");
                proxyOut = socket.getOutputStream();
                proxyIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Problem accepting socket: " + e.getMessage());
            }
            
            WebrtcHandler webrtcHandler = null;

            /**
             * We only accept 1 socket at a time.  When we get a connection we
             * enter the loop below and process requests from that particular socket.
             * When that socket closes, then we go back to accept() and wait for a new
             * connection.
             */
            try {
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
                    	handleTouch(msg.getTouch());
                    	break;
                    case SENSOREVENT:
                        // use the thread pool to handle this
                    	handleSensor(msg.getSensor());
                    	break;
                    case INTENT:
                        intentHandler.handleMessage(msg);
                        break;
                    case LOCATION:
                        locationHandler.handleMessage(msg);
                        break;
                    case VIDEO_PARAMS:
                        webrtcHandler = new WebrtcHandler(this, msg.getVideoInfo());
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
                    default:
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error on socket: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // send a final BYE to fbstream via the webrtc helper
//                handleWebRTC(Request.newBuilder().setType(RequestType.WEBRTC)
//                    .setWebrtcMsg(WebRTCMessage.newBuilder().setType(WebRTCType.BYE))
//                    .build());
                
                try {
                    proxyIn.close();
                    proxyOut.close();
                    socket.close();
                } catch (Exception e) {
                    // Don't care
                }
            }
        }
    }
    
    private void disconnet() {
        
    }

    protected void sendMessage(Response message) {
        // use synchronized statement to ensure only one message gets sent at a time
        synchronized(sendMessageLock) {
    	    try {
                message.writeDelimitedTo(proxyOut);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                Log.e(TAG, "Socket write error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    protected Context getContext() {
        return context;
    }

    public abstract void handleScreenInfo(final Request message);
    public abstract void handleTouch(final TouchEvent event);

    private void handleSensor(final SensorEvent event) {
        // this SensorEvent was sent from the client, let's pass it on to the Sensor Message Unix socket
        sensorMsgExecutor.execute(new SensorMessageRunnable(this, sockfd, event));
    }

    public void handleRotationInfo(final Request request) {
        RotationInfo rotationInfo = request.getRotationInfo();
        int rotation = rotationInfo.getRotation(); // get rotation value from protobuf
        Intent intent = new Intent(ROTATION_CHANGED_ACTION); // set action (protected system broadcast)
        intent.putExtra("rotation", rotation); // add rotation value to intent
        context.sendBroadcast(intent); // send broadcast
    }

    // when we receive a Ping message from the client, pack it back up in a Response wrapper and return it
    public void handlePing(final Request request) {
        if (request.hasPingRequest() ) {
            // get the ping message that was sent from the client
            Ping ping = request.getPingRequest();

            // pack the ping message in a Response wrapper
            Response.Builder builder = Response.newBuilder();
            builder.setType(ResponseType.PING);
            builder.setPingResponse(ping);
            Response response = builder.build();

            // send the response to the client
            //try {
                sendMessage(response);
            //} catch (IOException e) {
                // don't care
            //}
        }
    }

    // called from the SensorMessageRunnable
    public void sendSensorEvent(int sockfd, SVMPSensorEventMessage message) {
        // send message
        SockClientWrite(sockfd, message);
    }

}
