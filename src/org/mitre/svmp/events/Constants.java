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
public interface Constants {
    public static final int PROXY_PORT = 8001;

    public static final String SVMP_BROADCAST_PERMISSION = "org.mitre.svmp.permission.SVMP_BROADCAST";

    public static final String ROTATION_CHANGED_ACTION = "org.mitre.svmp.action.ROTATION_CHANGED";
    public static final String LOCATION_SUBSCRIBE_ACTION = "org.mitre.svmp.action.LOCATION_SUBSCRIBE";
    public static final String LOCATION_UNSUBSCRIBE_ACTION = "org.mitre.svmp.action.LOCATION_UNSUBSCRIBE";
    public static final String INTERCEPT_NOTIFICATION_ACTION = "org.mitre.svmp.action.INTERCEPT_NOTIFICATION";
    public static final String LAUNCHER_STARTED_ACTION = "org.mitre.svmp.action.LAUNCHER_STARTED";
    public static final String KEYBOARD_ATTACHED_ACTION = "org.mitre.svmp.action.KEYBOARD_ATTACHED";
    public static final String KEYBOARD_DETACHED_ACTION = "org.mitre.svmp.action.KEYBOARD_DETACHED";
}
