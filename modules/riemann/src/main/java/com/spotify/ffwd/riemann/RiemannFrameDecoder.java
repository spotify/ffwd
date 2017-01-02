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
package com.spotify.ffwd.riemann;

import com.google.inject.Inject;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;

import java.util.List;

/**
 * Parses and unpacks length-prefixed streams of Proto.Msg messages.
 */
public class RiemannFrameDecoder extends ByteToMessageDecoder {
    private static final int MAX_SIZE = 0xffffff;

    @Inject
    private RiemannSerialization serializer;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
        throws Exception {
        if (in.readableBytes() < 4) {
            return;
        }

        final long length = in.getUnsignedInt(0);

        if (length > MAX_SIZE) {
            throw new CorruptedFrameException(
                String.format("frame size (%s) larger than max (%d)", length, MAX_SIZE));
        }

        final int intLength = (int) length;

        if (in.readableBytes() < (4 + length)) {
            return;
        }

        in.skipBytes(4);
        final ByteBuf frame = in.readBytes(intLength);

        try {
            out.add(serializer.parse0(frame));
        } finally {
            frame.release();
        }
    }
}
