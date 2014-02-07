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
package org.mitre.svmp.events.logs;

/**
 * @author Joe Portner
 */
import java.io.IOException;

public class LogCatThread extends BaseLogThread {
    private static final String LOGCAT_CLEAR = "/system/bin/logcat -c";
    private static final String LOGCAT_PROCSTRING = "/system/bin/logcat -v";

    public LogCatThread(String androidID, String address, int port, String format, String filter) {
        super(androidID, address, port, String.format("%s %s %s", LOGCAT_PROCSTRING, format, filter));
    }

    public void run() {
        try {
            // clear LogCat buffer (if the service restarts, we don't want to resend all of the old log entries)
            Runtime.getRuntime().exec(LOGCAT_CLEAR);
        } catch (IOException e) {
            e.printStackTrace();
        }

        super.run();
    }
}
