/*-
 * -\-\-
 * FastForward Riemann Module
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

package com.spotify.ffwd.riemann;

import com.aphyr.riemann.Proto;
import com.google.common.collect.ImmutableList;
import com.spotify.ffwd.protobuf250.InvalidProtocolBufferException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RiemannSerialization {
    private static final Logger log = LoggerFactory.getLogger(RiemannSerialization.class);

    Proto.Msg parse0(ByteBuf buffer) throws IOException {
        final InputStream inputStream = new ByteBufInputStream(buffer);

        try {
            return Proto.Msg.parseFrom(inputStream);
        } catch (final InvalidProtocolBufferException e) {
            throw new IOException("Invalid protobuf message", e);
        }
    }

    List<Object> decode0(Proto.Msg message) {
        log.error("Unsupported events sent to riemann input! Dropping data!");
        return ImmutableList.of();
    }
}
