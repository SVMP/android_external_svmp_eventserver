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

import android.hardware.input.InputManager;
import android.view.KeyEvent;
import org.mitre.svmp.protocol.SVMPProtocol;

import java.util.Date;

/**
 * @author Joe Portner
 * Receives KeyEvent request messages from the client and injects them into the system
 * Requires platform-level access to run properly (uses hidden APIs)
 */
public class KeyHandler {
    private static final String TAG = KeyHandler.class.getName();

    private InputManager inputManager;

    public KeyHandler() {
        inputManager = InputManager.getInstance();
    }

    public void handleKeyEvent(SVMPProtocol.KeyEvent msg) {
        // recreate the KeyEvent
        final KeyEvent keyEvent;
        if (msg.hasCharacters()) {
            keyEvent = new KeyEvent(msg.getDownTime(), msg.getCharacters(), msg.getDeviceId(), msg.getFlags());
        }
        else {
            // note: use our system time instead of message's eventTime, prevents "stale" errors
            keyEvent = new KeyEvent(msg.getDownTime(), new Date().getTime(), msg.getAction(), msg.getCode(), msg.getRepeat(),
                    msg.getMetaState(), msg.getDeviceId(), msg.getScanCode(), msg.getFlags(), msg.getSource());
        }

        inputManager.injectInputEvent(keyEvent, InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH);
    }
}
