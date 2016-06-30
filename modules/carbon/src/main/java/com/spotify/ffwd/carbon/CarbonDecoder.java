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
package com.spotify.ffwd.carbon;

import com.google.common.collect.Sets;
import com.spotify.ffwd.model.Metric;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.apache.commons.lang3.StringEscapeUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Sharable
public class CarbonDecoder extends MessageToMessageDecoder<String> {
    private static final Set<String> EMPTY_TAGS = Sets.newHashSet();
    private static final Pattern WHITE_SPACE_PATTERN = Pattern.compile("\\s+");

    private final String key;

    public CarbonDecoder(final String key) {
        this.key = key;
    }

    @Override
    protected void decode(
        final ChannelHandlerContext arg0, final String in, final List<Object> out
    ) throws Exception {

        final String[] tokens = WHITE_SPACE_PATTERN.split(in);

        if (tokens.length != 3) {
            throw new CorruptedFrameException(String.format("malformed carbon frame (%s)", in));
        }

        final double value;
        try {
            value = Double.valueOf(tokens[1]);
        } catch (final NumberFormatException e) {
            throw new CorruptedFrameException(
                String.format("malformed carbon frame (%s), (%s) is an invalid value", in,
                    StringEscapeUtils.escapeJava(tokens[1])));
        }

        final long timestamp;
        try {
            timestamp = Long.valueOf(tokens[2]);
        } catch (final NumberFormatException e) {
            throw new CorruptedFrameException(
                String.format("malformed carbon frame (%s), (%s) is an invalid timestamp", in,
                    StringEscapeUtils.escapeJava(tokens[2])));
        }

        final Map<String, String> tags = new HashMap<>();
        tags.put("what", tokens[0]);

        out.add(new Metric(key, value, new Date(timestamp), null, EMPTY_TAGS, tags, null));
    }
}
