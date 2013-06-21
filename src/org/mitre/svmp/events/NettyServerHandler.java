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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.MessageList;
import org.mitre.svmp.protocol.SVMPProtocol.Response;

/**
 * @author Joe Portner
 */
public class NettyServerHandler extends ChannelInboundHandlerAdapter {
    private static final String TAG = NettyServerHandler.class.getName();

    private NettyServer nettyServer; // passed from NettyServerInitializer

    // constructor
    public NettyServerHandler(NettyServer nettyServer) {
        this.nettyServer = nettyServer;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageList<Object> msgs) throws Exception {
        // save the ChannelHandlerContext so we can push messages to the client later
        nettyServer.updateCtx(ctx);

        int size = msgs.size();
        for (int i = 0; i < size; i++) {
            // get the Response that was sent from the vm's Helper service
            Response response = (Response) msgs.get(i);

            // a VMREADY response is a dummy message that is only used for handshaking to the Netty server;
            // receiving a message the only way to make the server aware of the
            // ChannelHandlerContext so it can push messages to the client
            if( response.getType() != Response.ResponseType.VMREADY )
                // pass the message on to the client
                nettyServer.baseServer.sendMessage(response);

            // print output
            //Log.e(TAG, "Received Response: " + response.toString());
            Utility.logError(TAG + ": Received Response: " + response.toString());
        }

        msgs.recycle();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //Log.e(TAG, "Unexpected exception from downstream: " + cause);
        Utility.logError(TAG + ": Unexpected exception from downstream: " + cause);
        ctx.close();
    }
}