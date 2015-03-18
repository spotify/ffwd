// $LICENSE
/**
 * Copyright 2013-2014 Spotify AB. All rights reserved.
 *
 * The contents of this file are licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 **/
package com.spotify.ffwd.riemann;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;

import com.google.common.base.Supplier;
import com.google.inject.Inject;
import com.spotify.ffwd.protocol.ProtocolServer;

/**
 * Decode a stream of data which is length-prefixed.
 *
 * Should only be used with TCP-based protocols.
 *
 * @author udoprog
 */
public class RiemannTCPProtocolServer implements ProtocolServer {
    @Inject
    private ChannelInboundHandler handler;

    @Inject
    private RiemannMessageDecoder decoder;

    @Inject
    private RiemannResponder responder;

    @Inject
    private Supplier<RiemannFrameDecoder> frameDecoder;

    @Override
    public final ChannelInitializer<Channel> initializer() {
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(frameDecoder.get(), decoder, responder, handler);
            }
        };
    }
}
