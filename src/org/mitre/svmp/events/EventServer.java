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
    private long lastDownTime, lastDownTimeClient = -1;
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

    private MotionEvent.PointerCoords translateCoords(float X, float Y) {
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();

        // Translate the client coordinates according to the screen orientation
        // Origin is the top left of the physical phone's screen in the natural upright position
        int rotation = Surface.ROTATION_0;
        try {
            rotation = windowManager.getRotation();
            switch (rotation) {
                case Surface.ROTATION_0:
                    coords.x = X;
                    coords.y = Y;
                    break;
                case Surface.ROTATION_180:
                    // screen turned left 180
                    // client origin is now in bottom right
                    // invert both
                    coords.x = screenSize.x - X;
                    coords.y = screenSize.y - Y;
                    break;
                case Surface.ROTATION_90:
                    // screen turned left 90
                    // client origin is now in bottom left
                    // switch, invert client x
                    coords.x = Y;
                    coords.y = screenSize.x - X;
                    break;
                case Surface.ROTATION_270:
                    // screen turned right 90
                    // client origin is now in top right
                    // switch, invert client y
                    coords.x = screenSize.y - Y;
                    coords.y = X;
                    break;
            }
        } catch (RemoteException re) {
            Log.e(TAG, "Cannot translate input coordinates. Error getting display size: " + re.getMessage());
            coords.x = X;
            coords.y = Y;
        } finally {
            coords.pressure = 1f;
            coords.size = 5f;
        }
        return coords;
    }

    @Override
    public void handleTouch(final List<TouchEvent> eventList) {
        // we can receive a batch of touch events; process each event individually
        for (TouchEvent event : eventList)
            handleTouch(event);
    }

    // overload to handle individual touch events
    public void handleTouch(final SVMPProtocol.TouchEvent event) {
        if (event.hasEventTime())
            handleTouchNew(event);
        else
            handleTouchOld(event);
    }

    private final void handleTouchOld(final SVMPProtocol.TouchEvent event) {
        // Maintain Downtime
        final long now = SystemClock.uptimeMillis();
        if (event.getAction()== MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_POINTER_1_DOWN ||
                event.getAction() == MotionEvent.ACTION_POINTER_2_DOWN)
        {
            lastDownTime = now;
        }

        // Create the MotionEvent to inject
        final int pointerSize = event.getItemsCount();
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointerSize];
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[pointerSize];

        for (int i = 0; i < pointerSize; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = event.getItems(i).getId();
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = translateCoords(event.getItems(i).getX(), event.getItems(i).getY());
        }

        MotionEvent me = MotionEvent.obtain(lastDownTime, now, event.getAction(), pointerSize, props, coords,
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);

        injectTouch(me);
    }

    private long lastTouch = 0;
    private void handleTouchNew(final SVMPProtocol.TouchEvent event) {
        // Maintain Downtime
        final long now = SystemClock.uptimeMillis();
        long eventTime = event.getEventTime();

        if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
            Log.d(TAG, "ACTION_DOWN( " + event.getItems(0).getId() + ") at " + now + " / " + eventTime);
            lastDownTime = now;
            lastDownTimeClient = event.getDownTime();
        }

        if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
            Log.d(TAG, "ACTION_UP( " + event.getItems(0).getId() + ") at " + now + " / " + eventTime);
            // give UP's a little bit of leeway in the timing 
            // makes them less likely to get dropped by the system and reduces touch input weirdness
            eventTime += 75;
        }

        if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_UP) {
            int index = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
            Log.d(TAG, "ACTION_POINTER_UP(" + event.getItems(index).getId() + ") at " + now + " / " + eventTime);
        }

        if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_DOWN) {
            int index = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
            Log.d(TAG, "ACTION_POINTER_DOWN(" + event.getItems(index).getId() + ") at " + now + " / " + eventTime);
        }

        // translate the pointer coordinates based on screen orientation
        final int pointerSize = event.getItemsCount();
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointerSize];
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[pointerSize];

        for (int i = 0; i < pointerSize; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = event.getItems(i).getId();
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = translateCoords(event.getItems(i).getX(), event.getItems(i).getY());
        }

        // use client edgeFlags if present
        int edgeFlags = event.hasEdgeFlags() ? event.getEdgeFlags() : 0;

        MotionEvent me = MotionEvent.obtain(
                lastDownTime,                           // downTime
                offsetEventTime(eventTime),             // eventTime
                event.getAction(),                      // action
                pointerSize,                            // pointerCount
                props,                                  // pointerProperties
                coords,                                 // pointerCoords
                0,                                      // metaState
                0,                                      // buttonState
                1,                                      // xPrecision
                1,                                      // yPrecision
                0,                                      // deviceId
                edgeFlags,                              // edgeFlags
                InputDevice.SOURCE_TOUCHSCREEN,         // source
                0 );                                    // flags

        // handle any batched historical pointer movements
        int historicalSize = event.getHistoricalCount();
        for (int i = 0; i < historicalSize; i++) {
            SVMPProtocol.TouchEvent.HistoricalEvent h = event.getHistorical(i);

            // translate the coordinate space to local screen orientation
            int coordsSize = event.getHistorical(i).getCoordsCount();
            coords = new MotionEvent.PointerCoords[coordsSize];
            for (int j = 0; j < coordsSize; j++) {
                coords[j] = translateCoords(h.getCoords(j).getX(), h.getCoords(j).getY());
            }

            me.addBatch(offsetEventTime(h.getEventTime()), coords, 0);
        }

        me.setAction(event.getAction());

        injectTouch(me);
    }

    private long offsetEventTime(long clientTime) {
        return clientTime - lastDownTimeClient + lastDownTime;
    }

    private void injectTouch(MotionEvent me) {
        try {
            //Log.d(TAG, "injecting touch event");
            //InputManager.getInstance().injectInputEvent(me,InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT);
            if (!InputManager.getInstance().injectInputEvent(me,InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH))
                Log.e(TAG, "Failed injecting MotionEvent " + me.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error injecting MotionEvent: " + e.getMessage());
        } finally {
            me.recycle();
        }
    }

}

