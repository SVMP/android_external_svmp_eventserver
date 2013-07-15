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

package org.mitre.svmp.protocol;

import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 * Superclass of all SensorEvent messages. Not instantiable itself except via
 * child classes and the decoder factory method.
 * 
 * See http://developer.android.com/reference/android/hardware/SensorEvent.html
 * 
 * @author dkeppler
 *
 */
public class SVMPSensorEventMessage {
	protected int type;
	protected int accuracy;
	protected long timestamp;
	protected float[] values;
		
	public SVMPSensorEventMessage(int type, int accuracy, long timestamp, float[] values) {
		this.type = type;
		this.accuracy = accuracy;
		this.timestamp = timestamp;
		this.values = values;
	}
	

	public int getAccuracy() {
		return accuracy;
	}

	public long getTimestamp() {
		return timestamp;
	}

}
