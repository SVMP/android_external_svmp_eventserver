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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import org.mitre.svmp.protocol.SVMPProtocol.*;
import org.mitre.svmp.protocol.SVMPSensorEventMessage;

import java.io.ByteArrayOutputStream;

/**
 * @author Joe Portner
 */
public class Utility {
    private static String TAG = Utility.class.getName();

    public static byte[] drawableToBytes (Drawable drawable) {
        byte[] value = null;
        if (drawable == null)
            return null;

        try {
            Bitmap bitmap;
            if (drawable instanceof BitmapDrawable)
                bitmap = ((BitmapDrawable)drawable).getBitmap();
            else {
                // get width and height of drawable
                int width = drawable.getIntrinsicWidth(),
                        height = drawable.getIntrinsicHeight();
                // if the width and height aren't valid, this is a probably a solid Color resource; set them manually
                if (width < 1)
                    width = 48;
                if (height < 1)
                    height = 48;
                // create a bitmap with the right width and height
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                // draw the drawable to the bitmap
                drawable.draw(canvas);
            }

            // take the bitmap and convert it to a byte array
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            value = stream.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Error converting drawable to bitmap: ", e);
        }

        return value;
    }

    // converts SensorEvent Protobuf to SVMPSensorEventMessage that can be written directly to the sensor Unix socket
    public static SVMPSensorEventMessage toSVMPMessage(SensorEvent sensorEvent) {
        // get values
        int type = sensorEvent.getType().getNumber();
        int accuracy = sensorEvent.getAccuracy();
        long timestamp = sensorEvent.getTimestamp();
        float[] values = new float[sensorEvent.getValuesCount()];
        for (int i = 0; i < values.length; i++) {
            values[i] = sensorEvent.getValues(i);
        }

        // generate and return the SVMPSensorEventMessage
        return new SVMPSensorEventMessage(type, accuracy, timestamp, values);
    }

    public static Location getLocation(LocationUpdate locationUpdate) {
        Location location = null;

        try {
            location = new Location(locationUpdate.getProvider());

            // get required fields
            location.setLatitude(locationUpdate.getLatitude());
            location.setLongitude(locationUpdate.getLongitude());
            location.setTime(locationUpdate.getTime());
            location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

            // get optional fields
            if( locationUpdate.hasAccuracy() )
                location.setAccuracy(locationUpdate.getAccuracy());
            if( locationUpdate.hasAltitude() )
                location.setAltitude(locationUpdate.getAltitude());
            if( locationUpdate.hasBearing() )
                location.setBearing(locationUpdate.getBearing());
            if( locationUpdate.hasSpeed() )
                location.setSpeed(locationUpdate.getSpeed());
        } catch( Exception e ) {
            Log.e(TAG, "Error parsing LocationUpdate: " + e.getMessage());
        }

        return location;
    }

    public static Bundle getBundle(LocationProviderStatus locationProviderStatus) {
        Bundle extras = new Bundle();

        // TODO: populate Bundle with extras from the Tuple list
        /*
        List<LocationProviderStatus.Tuple> tuples = locationProviderStatus.getExtrasList();
        for( LocationProviderStatus.Tuple tuple : tuples ) {
            extras.putString(tuple.getKey(), tuple.getValue());
        }
        */

        return extras;
    }

    public static Response buildSubscribeResponse(LocationSubscription locationSubscription, boolean singleShot) {
        LocationSubscribe.Builder lsBuilder = LocationSubscribe.newBuilder();
        lsBuilder.setType( singleShot
                ? LocationSubscribe.LocationSubscribeType.SINGLE_UPDATE
                : LocationSubscribe.LocationSubscribeType.MULTIPLE_UPDATES );
        lsBuilder.setProvider(locationSubscription.getProvider());
        lsBuilder.setMinTime(locationSubscription.getMinTime());
        lsBuilder.setMinDistance(locationSubscription.getMinDistance());

        LocationResponse.Builder lrBuilder = LocationResponse.newBuilder();
        lrBuilder.setType(LocationResponse.LocationResponseType.SUBSCRIBE);
        lrBuilder.setSubscribe(lsBuilder);

        return buildResponse(lrBuilder);
    }

    public static Response buildUnsubscribeResponse(String provider) {
        LocationUnsubscribe.Builder lusBuilder = LocationUnsubscribe.newBuilder();
        lusBuilder.setProvider(provider);

        LocationResponse.Builder lrBuilder = LocationResponse.newBuilder();
        lrBuilder.setType(LocationResponse.LocationResponseType.UNSUBSCRIBE);
        lrBuilder.setUnsubscribe(lusBuilder);

        return buildResponse(lrBuilder);
    }

    private static Response buildResponse(LocationResponse.Builder lrBuilder) {
        Response.Builder rBuilder = Response.newBuilder();
        rBuilder.setType(Response.ResponseType.LOCATION);
        rBuilder.setLocationResponse(lrBuilder);
        return rBuilder.build();
    }
}
