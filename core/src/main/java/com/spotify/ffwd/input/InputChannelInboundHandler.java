/*-
 * -\-\-
 * FastForward Core
 * --
 * Copyright (C) 2016 - 2018 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

package com.spotify.ffwd.input;

import com.google.inject.Inject;
import com.spotify.ffwd.model.v2.Batch;
import com.spotify.ffwd.model.v2.Metric;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Sharable
public class InputChannelInboundHandler extends ChannelInboundHandlerAdapter {

  private static final Logger log = LoggerFactory.getLogger(InputChannelInboundHandler.class);

  @Inject
  private InputManager input;

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (msg instanceof Metric) {
      input.receiveMetric((Metric) msg);
      return;
    }

    if (msg instanceof Batch) {
      input.receiveBatch((Batch) msg);
      return;
    }

    log.error("{}: Got garbage '{}' in channel, closing", ctx.channel(), msg);
    ctx.channel().close();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.info("{}: Error in channel, closing", ctx.channel(), cause);
    ctx.channel().close();
  }
}
