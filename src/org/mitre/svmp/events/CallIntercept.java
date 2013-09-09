/*
 * Copyright 2013 The MITRE Corporation, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this work except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mitre.svmp.events;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import org.mitre.svmp.protocol.SVMPProtocol.IntentAction;

/**
 * Intercepts certain intent start actions (see manifest) and sends them to the IntentHandler to be processed
 * @author Colin Courtney, Joe Portner
 */
public class CallIntercept extends Activity implements Constants {
    private static final String TAG = CallIntercept.class.getName();

    // we can't use a BroadcastReceiver to intercept activity intents, so we have this
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(INTERCEPT_INTENT_ACTION);
        intent.putExtra("intentActionValue", IntentAction.ACTION_DIAL.getNumber());
        intent.putExtra("data", getIntent().getDataString());
        sendBroadcast(intent, "org.mitre.svmp.permission.RECEIVE_INTERCEPT_INTENT");

        Log.d(TAG, "Intercepted call, sent data to IntentHandler: [data '" + getIntent().getDataString() + "']");
        finish();
    }
}
