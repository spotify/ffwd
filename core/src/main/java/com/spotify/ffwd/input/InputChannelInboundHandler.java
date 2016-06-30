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
package com.spotify.ffwd.input;

import com.google.inject.Inject;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Sharable
public class InputChannelInboundHandler extends ChannelInboundHandlerAdapter {
    @Inject
    private InputManager input;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Event) {
            input.receiveEvent((Event) msg);
            return;
        }

        if (msg instanceof Metric) {
            input.receiveMetric((Metric) msg);
            return;
        }

        log.error("{}: Got garbage '{}' in channel, closing", ctx.channel(), msg);
        ctx.channel().close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.info("{}: Error in channel, closing", ctx.channel(), cause);
        ctx.channel().close();
    }
}
