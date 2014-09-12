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

import android.content.Context;
import android.os.IPowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.IWindowManager;
import android.view.MotionEvent;
import android.view.WindowManagerImpl;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.input.InputManager;
import android.view.Display;
import android.view.InputDevice;
import android.view.Surface;
import android.graphics.Point;

import java.io.IOException;
import java.util.List;

import org.mitre.svmp.protocol.*;
import org.mitre.svmp.protocol.SVMPProtocol.Response.ResponseType;
import org.mitre.svmp.protocol.SVMPProtocol.TouchEvent;

public class EventServer extends BaseServer {
    private static final String TAG = EventServer.class.getName();

    private IWindowManager windowManager;
    private Display display;
    private long lastDownTime;
    private final Point screenSize = new Point();
    private double xScaleFactor, yScaleFactor;

    public EventServer(Context context) throws IOException {
        super(context);
        windowManager = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
        display = DisplayManagerGlobal.getInstance().getRealDisplay(Display.DEFAULT_DISPLAY);
 
        // try{
          // windowManager.getRealDisplaySize(screenSize);	  
          display.getRealSize(screenSize);
        // } catch (RemoteException re){
        //   Utility.logError("Error getting display size: " + re.getMessage());
        // }

        Log.d(TAG, "Display Size: " + screenSize.x + " , " + screenSize.y);

        start();
    }

    @Override
    public void handleScreenInfo(final SVMPProtocol.Request message) throws IOException {
        SVMPProtocol.Response.Builder msg = SVMPProtocol.Response.newBuilder();
        SVMPProtocol.ScreenInfo.Builder scr = SVMPProtocol.ScreenInfo.newBuilder();
        scr.setX(screenSize.x);
        scr.setY(screenSize.y);
        msg.setScreenInfo(scr);
        msg.setType(ResponseType.SCREENINFO);
        sendMessage(msg.build());

        Log.d(TAG, "Sent screen info response: " + screenSize.x + "," + screenSize.y);
    }

    @Override
    public void handleTouch(final List<TouchEvent> eventList) {
        // we can receive a batch of touch events; process each event individually
        for (TouchEvent event : eventList)
            handleTouch(event);
    }

    // overload to handle individual touch events
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
                Log.e(TAG, "Error getting display size: " + re.getMessage());
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
    	  //Utility.logInfo("injecting touch event");
          // windowManager.injectPointerEvent(me,false);
          InputManager.getInstance().injectInputEvent(me,InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT); 
       } catch (Exception e) {
         // ignore for now 
       } finally {
           me.recycle();
       }

    }

}
