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

import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.mitre.svmp.protocol.SVMPProtocol;
import org.mitre.svmp.protocol.SVMPProtocol.Request;
import org.mitre.svmp.protocol.SVMPProtocol.Response;
import org.mitre.svmp.protocol.SVMPProtocol.SensorEvent;
import org.mitre.svmp.protocol.SVMPProtocol.VideoRequest;
import org.mitre.svmp.protocol.SVMPProtocol.TouchEvent;
import org.mitre.svmp.protocol.SVMPSensorEventMessage;
import org.mitre.svmp.protocol.SVMPProtocol.Response.ResponseType;

import org.mitre.svmp.events.FbStreamEventMessage;
import org.mitre.svmp.events.FbStreamMessageRunnable;
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
    private static final String TAG = "BASE-EVENTSERVER";
    
    private native int InitSockClient(String path);
    private native int SockClientWrite(int fd,SVMPSensorEventMessage event);
    private native int SockClientClose(int fd); 
    private int sockfd;
    // fbstream
    private int fbstrfd;
    private native int InitFbStreamClient(String path);
    private native int FbStreamClientWrite(int fd,FbStreamEventMessage event);
    private native int FbStreamClientClose(int fd); 


    private ExecutorService sensorMsgExecutor;
    private ExecutorService fbstreamMsgExecutor;
    private NettyServer intentServer;
    private NettyServer locationServer;
    private final Object sendMessageLock = new Object();

    public BaseServer(final int port) throws IOException {
        sockfd = InitSockClient("/dev/socket/svmp_sensors");
        Utility.logInfo("InitSockClient returned " + sockfd);
        this.proxyPort = port;
	fbstrfd = InitFbStreamClient("/dev/socket/fbstr_command");
        Utility.logInfo("InitFbStreamClient returned " + fbstrfd);
    }
    
    static {
    	System.loadLibrary("remote_events_jni");
    }

    public void start() throws IOException {
        // We create a SingleThreadExecutor because it executes sequentially
        // this guarantees that sensor event messages will be sent in order
        sensorMsgExecutor = Executors.newSingleThreadExecutor();

        fbstreamMsgExecutor = Executors.newSingleThreadExecutor();

        // start Netty receivers for sending/receiving Intent and Location messages
        startNettyServers();

        this.proxySocket = new ServerSocket(proxyPort);
        Utility.logInfo("Event server listening on proxyPort " + proxyPort);
        this.run();
    }

    public void startNettyServers() {
        // start a new thread to receive Intent responses from the IntentHelper
        intentServer = new NettyServer(this, NETTY_INTENT_PORT);
        intentServer.start();

        // start a new thread to receive Location responses from the LocationHelper
        locationServer = new NettyServer(this, NETTY_LOCATION_PORT);
        locationServer.start();
    }

    protected void run() {
        while (true) {
            Socket socket = null;
            try {
                socket = proxySocket.accept();
                Utility.logInfo("Socket connected");
                proxyOut = socket.getOutputStream();
                proxyIn = socket.getInputStream();
            } catch (IOException e) {
                Utility.logError("Problem accepting socket: " + e.getMessage());
            }

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
                        // use the thread pool to handle this
                        handleIntent(msg);
                        break;
                    case LOCATION:
                        // use the thread pool to handle this
                        handleLocation(msg);
                        break;
		    case VIDEO_STOP:
			Log.e(TAG,"!!VIDEO_STOP request received!\n");
			handleVideo(msg.getVideoRequest(),FbStreamEventMessage.STOP);
			break;
		    case VIDEO_START:
		    case VIDEO_PARAMS:
			Log.e(TAG,"VIDEO_START request received!\n");
			handleVideo(msg.getVideoRequest(),FbStreamEventMessage.START);
			break;
                    }
                }
            } catch (Exception e) {
                Utility.logError("Error on socket: " + e.getMessage());
                e.printStackTrace();
            } finally {
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

    protected void sendMessage(Response message) throws IOException {
        // use synchronized statement to ensure only one message gets sent at a time
        synchronized(sendMessageLock) {
    	    message.writeDelimitedTo(proxyOut);
        }
    }

    public abstract void handleScreenInfo(final Request message);
    public abstract void handleTouch(final TouchEvent event);

    private void handleSensor(final SensorEvent event) {
        // this SensorEvent was sent from the client, let's pass it on to the Sensor Message Unix socket
        sensorMsgExecutor.execute(new SensorMessageRunnable(this, sockfd, event));
    }
    private void handleVideo(final VideoRequest event, int cmd) {
        // this VideoEvent was sent from the client, let's pass it on to the FBstream Message Unix socket
        fbstreamMsgExecutor.execute(new FbStreamMessageRunnable(this, fbstrfd, event,cmd));
	// send a response
        try{
		SVMPProtocol.Response.Builder msg = SVMPProtocol.Response.newBuilder();
		if (cmd == FbStreamEventMessage.START )
			msg.setType(ResponseType.VIDEOSTART);
		else
			msg.setType(ResponseType.VIDEOSTOP);
		sendMessage(msg.build());
        } catch (IOException ioe){
		Utility.logError("Problem w/ response message:  " + ioe.getMessage());
        }
    }
    public void handleIntent(final Request request){
        // this Intent was sent from the client, let's pass it on to the IntentHelper
        intentServer.sendMessage(request);
    }
    public void handleLocation(final Request request){
        // this LocationUpdate was sent from the client, let's pass it on to the LocationHelper
        locationServer.sendMessage(request);
    }

    // called from the SensorMessageRunnable
    public void sendSensorEvent(int sockfd, SVMPSensorEventMessage message) {
        // send message
        SockClientWrite(sockfd, message);
    }
    // called from the FbStreamMessageRunnable
    public void sendFbStreamEvent(int sockfd, FbStreamEventMessage message) {
        // send message
        FbStreamClientWrite(sockfd, message);
    }
    /*
    public abstract void logError(final String message);
    public abstract void logInfo(final String message);
    */
}
