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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import org.mitre.svmp.protocol.SVMPProtocol.*;

/**
 * @author Joe Portner
 */
public class BaseHandler extends BroadcastReceiver implements Constants{
    protected BaseServer baseServer;

    // register a receiver without requiring any sender permissions
    public BaseHandler(BaseServer baseServer, String... filterActions) {
        this(null, baseServer, filterActions);
    }

    // register a receiver, if "permission" is not null then it requires that the sender has that permission
    public BaseHandler(String permission, BaseServer baseServer, String... filterActions) {
        super();
        this.baseServer = baseServer;

        if( filterActions.length > 0 ) {
            // register this BroadcastReceiver for the intent actions that we want
            IntentFilter intentFilter = new IntentFilter();
            for( int i = 0; i < filterActions.length; i++ )
                intentFilter.addAction(filterActions[i]);
            if (permission == null)
                baseServer.getContext().registerReceiver(this, intentFilter);
            else
                baseServer.getContext().registerReceiver(this, intentFilter, permission, null);
        }
    }

    // receive message from Android components, pass them back to the client
    public void onReceive(Context context, Intent intent) {
        // override in child class
    }

    // send a Response to the EventServer
    protected void sendMessage(Response response) {
        if( baseServer != null ) {
            baseServer.sendMessage(response);
        }
    }
}
