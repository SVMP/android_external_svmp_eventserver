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

/**
 * @author Joe Portner
 */
public class LocationSubscription {
    private String provider;
    private long minTime = 0L;
    private float minDistance = 0.0f;

    public LocationSubscription(String provider, long minTime, float minDistance) {
        this.provider = provider;
        this.minTime = minTime;
        this.minDistance = minDistance;
    }

    public String getProvider() {
        return provider;
    }

    public long getMinTime() {
        return minTime;
    }

    public float getMinDistance() {
        return minDistance;
    }

    public boolean satisfies(LocationSubscription properties) {
        return minTime <= properties.getMinTime() && minDistance <= properties.getMinDistance();
    }
}
