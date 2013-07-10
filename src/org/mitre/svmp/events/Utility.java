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

import org.mitre.svmp.protocol.SVMPProtocol.SensorEvent;
import org.mitre.svmp.protocol.SVMPSensorEventMessage;
import org.mitre.svmp.protocol.SVMPProtocol.VideoRequest;

/**
 * @author Joe Portner
 */
public class Utility {
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
    public static FbStreamEventMessage toSVMPMessage(VideoRequest Request) {
	int cmd = FbStreamEventMessage.START;
	String IP = Request.getIp();
	int port = Request.getPort();
	int bitrate = 0;
	if (Request.hasBitrate())
		bitrate = Request.getBitrate();
        return new FbStreamEventMessage(cmd,IP,port);

    }

    public static void logError(final String message) {
        //Log.e(TAG, message);
        System.err.println(message);
    }
    public static void logInfo(final String message) {
        //Log.i(TAG, message);
        System.out.println(message);
    }
}
