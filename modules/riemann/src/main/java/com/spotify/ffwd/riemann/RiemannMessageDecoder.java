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

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

import com.aphyr.riemann.Proto;
import com.google.inject.Inject;

@Sharable
public class RiemannMessageDecoder extends MessageToMessageDecoder<Proto.Msg> {
    @Inject
    private RiemannSerialization serializer;

    @Override
    protected void decode(ChannelHandlerContext ctx, Proto.Msg msg, List<Object> out) throws Exception {
        out.add(serializer.decode0(msg));
    }
}