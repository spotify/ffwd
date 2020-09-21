/*-
 * -\-\-
 * FastForward Protobuf Module
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

package com.spotify.ffwd.protobuf;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.InvalidProtocolBufferException;
import com.spotify.ffwd.FastForward;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.model.v2.Value;
import com.spotify.ffwd.protocol0.Protocol0;
import com.spotify.ffwd.protocol1.Protocol1;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Sharable
public class ProtobufDecoder extends MessageToMessageDecoder<ByteBuf> {
    private static final Logger log = LoggerFactory.getLogger(ProtobufDecoder.class);
    public static final int MAX_FRAME_SIZE = 0xffffff;

    @Override
    protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out)
        throws Exception {
        while (in.readableBytes() >= 8) {
            decodeOne(in, out);
        }

        if (in.readableBytes() > 0) {
            log.error("Garbage left in buffer, " + in.readableBytes() +
                " readable bytes have not been processed");
        }
    }

    private void decodeOne(final ByteBuf in, final List<Object> out)
        throws Exception {
        final int version = (int) in.getUnsignedInt(0);
        final long totalLength = in.getUnsignedInt(4);

        if (totalLength > MAX_FRAME_SIZE) {
            log.error(
                "Received frame with length (" + totalLength + ") larger than maximum allowed ( " +
                    MAX_FRAME_SIZE + ")");
            in.clear();
            return;
        }

        // datagram underflow
        if (in.readableBytes() < totalLength) {
            log.error(
                "Received frame of shorter length (" + in.readableBytes() + ") than reported (" +
                    totalLength + ")");
            in.clear();
            return;
        }

        in.skipBytes(8);

        final Object frame;

        switch (version) {
            case 0:  //TODO update this
                frame = decodeFrame(in, FastForward.Version.V0);
                break;
            case 1 :
                frame = decodeFrame(in, FastForward.Version.V1);
                break;
            default:
                throw new IllegalArgumentException("Unsupported protocol version: " + version);
        }

        if (frame != null) {
            out.add(frame);
        }
    }


    private Object decodeFrame(ByteBuf buffer, FastForward.Version version) throws Exception {


        final InputStream inputStream = new ByteBufInputStream(buffer);

        try {
            if (FastForward.Version.V0.equals(version)) {
                return decodeMetric0(Protocol0.Message.parseFrom(inputStream));
            }
            if (FastForward.Version.V1.equals(version)) {
                return decodeMetric1(com.spotify.ffwd.protocol1.Protocol1
                        .Message.parseFrom(inputStream));
            }
        } catch (final InvalidProtocolBufferException e) {
            throw new Exception("Invalid protobuf message version = " + version.getVersion(), e);
        }
        return null;
    }

    private Object decodeMetric0(final Protocol0.Message message) {
        if (!message.hasMetric()) {
            return null;
        }
        Protocol0.Metric metric = message.getMetric();
        final String key = metric.hasKey() ? metric.getKey() : null;
        final double value = metric.hasValue() ? metric.getValue() : Double.NaN;
        final Date time = metric.hasTime() ? new Date(metric.getTime()) : null;
        final String host = metric.hasHost() ? metric.getHost() : null;
        final Set<String> riemannTags = new HashSet<>(metric.getTagsList());
        final Map<String, String> tags = convertAttributes0(metric.getAttributesList());
        // TODO: support resource identifiers.
        final Map<String, String> resource = ImmutableMap.of();
        final String proc = metric.hasProc() ? metric.getProc() : null;

        if (host != null) {
            tags.put("host", host);
        }

        return new Metric(key, value, time, riemannTags, tags, resource, proc);
    }

    private Object decodeMetric1(final Protocol1.Message message) {
        if (!message.hasMetric()) {
            return null;
        }
        com.spotify.ffwd.protocol1.Protocol1.Metric metric = message.getMetric();
        final String key =  metric.getKey();
        final com.spotify.ffwd.model.v2.Value value = extractPointValue(metric);
        final String host =  metric.getHost();

        final Map<String, String> tags = convertAttributes1(metric.getAttributesList());
        // TODO: support resource identifiers.
        final Map<String, String> resource = ImmutableMap.of();


        if (host != null) {
            tags.put("host", host);
        }

        return new com.spotify.ffwd.model.v2.Metric(key, value, metric.getTime(), tags, resource);
    }

    private com.spotify.ffwd.model.v2.Value extractPointValue(
            com.spotify.ffwd.protocol1.Protocol1.Metric metric) {
        if (!metric.hasValue()) {
            return  Value.DoubleValue.create(Double.NaN);
            //TODO determine why Double.NaN is used as default
        }

        com.spotify.ffwd.protocol1.Protocol1.Value value = metric.getValue();

        if (value.getDistributionValue() == null) {
            return Value.DoubleValue.create(value.getDoubleValue());
        }
        return Value.DistributionValue.create(value.getDistributionValue());
    }

    private Map<String, String> convertAttributes1(
            List<com.spotify.ffwd.protocol1.Protocol1.Attribute> attributesList) {
        final Map<String, String> attributes = new HashMap<>();

        for (final com.spotify.ffwd.protocol1.Protocol1.Attribute a : attributesList) {
            attributes.put(a.getKey(), a.getValue());
        }

        return attributes;
    }

    private Map<String, String> convertAttributes0(List<Protocol0.Attribute> attributesList) {
        final Map<String, String> attributes = new HashMap<>();

        for (final Protocol0.Attribute a : attributesList) {
            attributes.put(a.getKey(), a.getValue());
        }

        return attributes;
    }
}
