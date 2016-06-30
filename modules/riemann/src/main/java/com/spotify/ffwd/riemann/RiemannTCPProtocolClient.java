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

import com.google.inject.Inject;
import com.spotify.ffwd.protocol.ProtocolClient;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class RiemannTCPProtocolClient implements ProtocolClient {
    private static final int MAX_LENGTH = 0xffffff;
    private static final int WARNING_ACK_THRESHOLD = 100;

    @Inject
    private RiemannSerialization serializer;

    private final AtomicInteger pending = new AtomicInteger();

    private final ChannelInboundHandlerAdapter receiver = new ChannelInboundHandlerAdapter() {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            final int p = pending.decrementAndGet();

            log.debug("Decrementing pending acks (current: {})", p);

            if (p > WARNING_ACK_THRESHOLD) {
                log.warn("number of pending acks are high ({})", p);
            }
        }

        @Override
        public boolean isSharable() {
            return true;
        }
    };

    private final ChannelOutboundHandlerAdapter sender = new ChannelOutboundHandlerAdapter() {
        @SuppressWarnings("unchecked")
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
            throws Exception {
            final ByteBuf buf;

            if (msg instanceof Collection) {
                buf = serializer.encodeAll0((Collection<Object>) msg);
            } else {
                buf = serializer.encode0(msg);
            }

            log.debug("Incrementing pending acks (current: {})", pending.incrementAndGet());

            ctx.write(buf, promise);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("error when sending");
        }

        @Override
        public boolean isSharable() {
            return true;
        }
    };

    @Override
    public ChannelInitializer<Channel> initializer() {
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                final LengthFieldBasedFrameDecoder lengthPrefix =
                    new LengthFieldBasedFrameDecoder(MAX_LENGTH, 0, 4);
                ch.pipeline().addLast(lengthPrefix, receiver, sender);
            }
        };
    }
}
