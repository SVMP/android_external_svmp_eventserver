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
import android.os.IPowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.IWindowManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManagerImpl;
//import android.hardware.input.InputManager;
import android.view.InputDevice;
import android.view.Surface;
import android.graphics.Point;

import java.io.IOException;
import org.mitre.svmp.protocol.*;
import org.mitre.svmp.protocol.SVMPProtocol.Response.ResponseType;

/**
 * @author Dave Bryson
 */
public class EventServer extends BaseServer {
    private static final String TAG = "EVENTSERVER";

    private static final int PORT = 8001;
    private IWindowManager windowManager;
    private long lastDownTime;
    private final Point screenSize = new Point();
    private double xScaleFactor, yScaleFactor;


    public EventServer() throws IOException {
        super(PORT);
        windowManager = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
                       
        try{
          windowManager.getRealDisplaySize(screenSize);
        } catch (RemoteException re){
          logError("Error getting display size: " + re.getMessage());
        }
        
        logInfo("Display Size: " + screenSize.x + " , " + screenSize.y);
         
        start();
        wake();
    }

    @Override
    public void handleScreenInfo(final SVMPProtocol.Request message){
        try{
        	SVMPProtocol.Response.Builder msg = SVMPProtocol.Response.newBuilder();
        	SVMPProtocol.ScreenInfo.Builder scr = SVMPProtocol.ScreenInfo.newBuilder();
        	scr.setX(screenSize.x);
        	scr.setY(screenSize.y);
        	msg.setScreenInfo(scr);
        	msg.setType(ResponseType.SCREENINFO);
        	sendMessage(msg.build());
        	
        	logInfo("Sent screen info response: " + screenSize.x + "," + screenSize.y);
        } catch (IOException ioe){
            logError("Problem handling message:  " + ioe.getMessage());
        }
    }

    @Override
    public void handleTouch(final SVMPProtocol.TouchEvent event) {
        // Maintain Downtime
        final long now = SystemClock.uptimeMillis();
        if (event.getAction()== MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_POINTER_1_DOWN ||
                event.getAction() == MotionEvent.ACTION_POINTER_2_DOWN)
            lastDownTime = now;
        // Create the MotionEvent to inject
        final int pointerSize = event.getItemsCount();
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointerSize];
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[pointerSize];
        for (int i = 0; i < pointerSize; i++) {
            props[i] = new MotionEvent.PointerProperties();
            coords[i] = new MotionEvent.PointerCoords();
            props[i].id = event.getItems(i).getId();
            props[i].toolType = 0;
            // Added testing scale - Problem with calculating scale correctly - goes to 0
        //final float newX = event.pointerItems.get(i).x * 0.4f;
            // origin is the top left of the physical phone's screen in portrait
            int rotation = Surface.ROTATION_0;
            try{
                rotation = windowManager.getRotation();
            } catch (RemoteException re){
                logError("Error getting display size: " + re.getMessage());
            }
            switch (rotation) {
                case Surface.ROTATION_0:
                    coords[i].x = event.getItems(i).getX();
                    coords[i].y = event.getItems(i).getY();
                    break;
                case Surface.ROTATION_180:
                	// screen turned left 180
                	// client origin is now in bottom right
                	// invert both
                	coords[i].x = screenSize.x - event.getItems(i).getX();
                	coords[i].y = screenSize.y - event.getItems(i).getY();
                    break;
                case Surface.ROTATION_90:
                	// screen turned left 90
                	// client origin is now in bottom left
                	// switch, invert client x
                    coords[i].x = event.getItems(i).getY();
                    coords[i].y = screenSize.x - event.getItems(i).getX();
                    break;
                case Surface.ROTATION_270:
                	// screen turned right 90
                	// client origin is now in top right
                	// switch, invert client y
                	coords[i].x = screenSize.y - event.getItems(i).getY();
                	coords[i].y = event.getItems(i).getX();                       	
                    break;
            }
            coords[i].pressure = 1f;
            coords[i].size = 5f;
        }

        MotionEvent me = MotionEvent.obtain(lastDownTime, now, event.getAction(), pointerSize, props, coords,
                                            0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
       try {
    	  //logInfo("injecting touch event");
          windowManager.injectPointerEvent(me,false);
          //InputManager.getInstance().injectInputEvent(me,InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT); 
       } catch (Exception e) {
         // ignore for now 
       } finally {
           me.recycle();
       }

    }

    /**
     * Wake the Device
     *
     * @return true if successful else false
     */
    private static boolean wake() {
        IPowerManager pm =
                IPowerManager.Stub.asInterface(ServiceManager.getService(Context.POWER_SERVICE));
        try {
            pm.userActivityWithForce(SystemClock.uptimeMillis(), true, true);
        } catch (RemoteException e) {
            return false;
        }
        return true;
    }

    @Override
    public void logError(final String message) {
        //Log.e(TAG, message);
    	System.err.println(message);
    }

    @Override
    public void logInfo(final String message) {
        //Log.i(TAG, message);
    	System.out.println(message);
    }


    public static void main(String[] args) throws IOException {
        new EventServer();
    }

}
