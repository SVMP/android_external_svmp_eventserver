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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import org.mitre.svmp.protocol.SVMPProtocol;
import org.mitre.svmp.protocol.SVMPSensorEventMessage;

/**
 * Base, single-threaded, 1 socket at a time, TCP Server.  The server uses a queue to process
 * messages.
 * @author Dave Bryson
 */
public abstract class BaseServer {
    private ServerSocket serverSocket;
    private OutputStream out = null;
    private InputStream in = null;
    private int port;
    private static final int BUFFER_SIZE = 8 * 1024;
    private static final String TAG = "BASE-EVENTSERVER";
    
    private native int InitSockClient(String path);
    //private static native int SockClientWrite(int fd,byte[] buf, int len);    
    private native int SockClientWrite(int fd,SVMPSensorEventMessage event);    
    private native int SockClientClose(int fd); 
    private int sockfd;

    public BaseServer(final int port) throws IOException {
        sockfd = InitSockClient("/dev/socket/svmp_sensors");
        logInfo("InitSockClient returned " + sockfd);
        this.port = port;
    }
    
    static {
    	System.loadLibrary("remote_events_jni");
    }

    public void start() throws IOException {
        this.serverSocket = new ServerSocket(port);
        logInfo("Event server listening on port " + port);
        this.run();
    }

    protected void run() {
        while (true) {
            Socket socket = null;
            try {
                socket = serverSocket.accept();
                logInfo("Socket connected");
                out = socket.getOutputStream();
                in = socket.getInputStream();
            } catch (IOException e) {
                logError("Problem accepting socket: " + e.getMessage());
            }

            /**
             * We only accept 1 socket at a time.  When we get a connection we
             * enter the loop below and process requests from that particular socket.
             * When that socket closes, then we go back to accept() and wait for a new
             * connection.
             */
            try {
                while (socket.isConnected()) {
                    SVMPProtocol.Request msg = SVMPProtocol.Request.parseDelimitedFrom(in);
                    //logInfo("Received message " + msg.getType().name());
                    
                    switch(msg.getType()) {
                    case SCREENINFO:
                    	handleScreenInfo(msg);
                    	break;
                    case TOUCHEVENT:
                    	handleTouch(msg.getTouch());
                    	break;
                    case SENSOREVENT:
                    	handleSensor(msg.getSensor());
                    	break;
                    }
                }
            } catch (Exception e) {
                logError("Error on socket: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    in.close();
                    out.close();
                    socket.close();
                } catch (Exception e) {
                    // Don't care
                }
            }
        }
    }

    protected void sendMessage(SVMPProtocol.Response message) throws IOException {
    	message.writeDelimitedTo(out);
    }

    private void handleSensor(final SVMPProtocol.SensorEvent message) {
        int type = message.getType().getNumber();
        int accuracy = message.getAccuracy();
        long timestamp = message.getTimestamp();
        float[] values = new float[message.getValuesCount()];
        for (int i = 0; i < values.length; i++) {
        	values[i] = message.getValues(i);
        }
        
        //logInfo("Forwarding sensor input to sensor lib");
        SockClientWrite(sockfd, new SVMPSensorEventMessage(type, accuracy, timestamp, values));
    }

    public abstract void handleScreenInfo(final SVMPProtocol.Request message);
    public abstract void handleTouch(final SVMPProtocol.TouchEvent event);


    public abstract void logError(final String message);
    public abstract void logInfo(final String message);
}
