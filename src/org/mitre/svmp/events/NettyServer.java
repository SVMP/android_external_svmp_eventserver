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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.mitre.svmp.protocol.SVMPProtocol.Request;

/**
 * @author Joe Portner
 */
public class NettyServer extends Thread {
    protected final BaseServer baseServer; // passed from BaseServer
    private final int port;
    private ChannelHandlerContext ctx;

    public NettyServer(BaseServer baseServer, int port) {
        this.baseServer = baseServer;
        this.port = port;
    }

    public void run() {
        Utility.logInfo("Netty receiver starting up... (port " + this.port + ")");

        // create the Boss EventLoopGroup and one Worker EventLoopGroup (resource intensive)
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        // try to start the server
        try {
            // create a new Bootstrap
            ServerBootstrap b = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new NettyServerInitializer(this));

            // bind this server to the port, open a channel, and wait for Requests
            // (doesn't stop until the thread has been interrupted)
            b.bind(port).sync().channel().closeFuture().sync();
        } catch( InterruptedException ie ) {
            Utility.logInfo("Netty receiver shutting down... (port " + this.port + ")");
        } finally {
            // Free resources from both EventLoopGroups
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    protected void updateCtx(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    protected void sendMessage(Request request) {
        try {
            if( this.ctx != null )
                this.ctx.channel().write(request);
            else
                Utility.logInfo("Netty server doesn't have a connected client");
        } catch( Exception e ) {
            Utility.logError("Netty server sendMessage failed: " + e.getMessage());
        }
    }
}