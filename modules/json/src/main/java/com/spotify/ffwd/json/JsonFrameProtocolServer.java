/*-
 * -\-\-
 * FastForward JSON Module
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

package com.spotify.ffwd.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.spotify.ffwd.netty.DatagramPacketToByteBuf;
import com.spotify.ffwd.protocol.ProtocolServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;

public class JsonFrameProtocolServer implements ProtocolServer {

  @Inject
  @Named("application/json")
  private ObjectMapper mapper;

  @Inject
  private ChannelInboundHandler handler;

  @Inject
  private JsonObjectMapperDecoder decoder;

  @Override
  public final ChannelInitializer<Channel> initializer() {
    return new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        ch.pipeline().addLast(new DatagramPacketToByteBuf());
        ch.pipeline().addLast(decoder, handler);
      }
    };
  }
}
