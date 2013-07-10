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

import org.mitre.svmp.protocol.SVMPProtocol.VideoRequest;
import org.mitre.svmp.events.FbStreamEventMessage;

/**
 * @author Joe Portner
 */
public class FbStreamMessageRunnable implements Runnable
{
    private BaseServer server;
    private int sockfd;
    private VideoRequest event;

    FbStreamMessageRunnable(BaseServer server, int sockfd, VideoRequest event) 
    {
        this.server = server;
        this.sockfd = sockfd;
        this.event = event;
    }

    public void run () {
        // construct SVMPSensorEventMessage
	FbStreamEventMessage  message = Utility.toSVMPMessage(event);
        // send the message to the Unix socket
        server.sendFbStreamEvent(sockfd, message);
    }
}
