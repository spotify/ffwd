/*
 * Copyright 2013-2017 Spotify AB. All rights reserved.
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
 */
package com.spotify.ffwd.http;

import com.google.inject.Inject;
import com.spotify.ffwd.protocol.ProtocolServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import lombok.extern.slf4j.Slf4j;

/**
 * Decode individual frames, should only be used with UDP protocols.
 *
 * @author udoprog
 */
@Slf4j
public class HttpProtocolServer implements ProtocolServer {
    @Inject
    private ChannelInboundHandler handler;

    @Inject
    private HttpDecoder decoder;

    @Override
    public final ChannelInitializer<Channel> initializer() {
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                final ChannelInboundHandlerAdapter exceptionHandler =
                    new ChannelInboundHandlerAdapter() {
                        @Override
                        public void exceptionCaught(
                            final ChannelHandlerContext ctx, final Throwable cause
                        ) throws Exception {
                            if (cause instanceof HttpException) {
                                final HttpException e = (HttpException) cause;
                                sendResponse(ctx, e.getStatus());
                                return;
                            }

                            if (cause instanceof DecoderException) {
                                exceptionCaught(ctx, cause.getCause());
                                return;
                            }

                            log.error("error in pipeline: ", cause);
                            sendResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                        }
                    };
                ch
                    .pipeline()
                    .addLast(new HttpRequestDecoder(), new HttpContentDecompressor(),
                        new HttpObjectAggregator(Integer.MAX_VALUE), decoder, exceptionHandler,
                        handler);
                ch.pipeline().addLast(new HttpResponseEncoder());
            }
        };
    }

    private void sendResponse(
        final ChannelHandlerContext ctx, final HttpResponseStatus status
    ) {
        ctx
            .channel()
            .writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status))
            .addListener((ChannelFutureListener) future -> future.channel().close());
    }
}
