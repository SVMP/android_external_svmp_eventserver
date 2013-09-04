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

import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import org.mitre.svmp.protocol.SVMPProtocol.*;
import org.mitre.svmp.protocol.SVMPSensorEventMessage;

/**
 * @author Joe Portner
 */
public class Utility {
    private static String TAG = Utility.class.getName();

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
    public static FbStreamEventMessage toSVMPMessage(VideoRequest Request, int CMD) {
	//int cmd = FbStreamEventMessage.START;
	int cmd = CMD;
	String IP = Request.getIp();
	int port = Request.getPort();
	int bitrate = 0;
	if (Request.hasBitrate())
		bitrate = Request.getBitrate();
        return new FbStreamEventMessage(cmd,IP,port);

    }

    public static Location getLocation(LocationUpdate locationUpdate) {
        Location location = null;

        try {
            location = new Location(locationUpdate.getProvider());

            // get required fields
            location.setLatitude(locationUpdate.getLatitude());
            location.setLongitude(locationUpdate.getLongitude());
            location.setTime(locationUpdate.getTime());

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
        lusBuilder.setProvider(provider);;

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
