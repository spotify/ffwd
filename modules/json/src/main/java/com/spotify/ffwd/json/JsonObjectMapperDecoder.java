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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.spotify.ffwd.model.v2.Metric;
import com.spotify.ffwd.model.v2.Value;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Sharable
public class JsonObjectMapperDecoder extends MessageToMessageDecoder<ByteBuf> {

  private static final Logger log = LoggerFactory.getLogger(JsonObjectMapperDecoder.class);
  private static final String HOST = "host";
  public static final Set<String> EMPTY_TAGS = Sets.newHashSet();
  public static final Map<String, String> EMPTY_ATTRIBUTES = new HashMap<>();
  public static final Map<String, String> EMPTY_RESOURCES = new HashMap<>();


  @Inject
  @Named("application/json")
  private ObjectMapper mapper;

  JsonObjectMapperDecoder() {
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
    if (!in.isReadable()) {
      return;
    }

    final Object frame;

    try {
      frame = decode0(in, out);
    } catch (Exception e) {
      log.error("Discarding invalid frame", e);
      return;
    }

    out.add(frame);
  }

  private Object decode0(ByteBuf in, List<Object> out) throws IOException {
    final JsonNode tree;

    try (final InputStream input = new ByteBufInputStream(in)) {
      tree = mapper.readTree(input);
    }

    final JsonNode typeNode = tree.get("type");

    if (typeNode == null) {
      throw new IllegalArgumentException("Missing field 'type'");
    }

    final String type = typeNode.asText();

    if ("metric".equals(type)) {
      return decodeMetric(tree, out);
    }

    throw new IllegalArgumentException("Invalid metric type '" + type + "'");
  }

  private Object decodeMetric(JsonNode tree, List<Object> out) {
    final String key = decodeString(tree, "key");
    final double value = decodeDouble(tree, "value");
    final long time = decodeTime(tree, "time");
    final Optional<String> host = Optional.ofNullable(decodeString(tree, HOST));
    final Map<String, String> tags = decodeAttributes(tree, "attributes");
    final Map<String, String> resource = decodeResources(tree, "resource");

    host.ifPresent(h -> tags.put(HOST, h));

    return new Metric(key, Value.DoubleValue.create(value), time, tags, resource);
  }

  private long decodeTime(JsonNode tree, String name) {
    final JsonNode n = tree.get(name);

    if (n == null) {
      return 0;
    }

    return n.asLong();
  }

  private double decodeDouble(JsonNode tree, String name) {
    final JsonNode n = tree.get(name);

    if (n == null) {
      return Double.NaN;
    }

    return n.asDouble();
  }

  String decodeString(JsonNode tree, String name) {
    final JsonNode n = tree.get(name);

    if (n == null || n.isNull()) {
      return null;
    }

    return n.asText();
  }

  private Map<String, String> decodeAttributes(JsonNode tree, String name) {
    final JsonNode n = tree.get(name);

    if (n == null) {
      return EMPTY_ATTRIBUTES;
    }

    if (n.getNodeType() != JsonNodeType.OBJECT) {
      return EMPTY_ATTRIBUTES;
    }

    final Map<String, String> attributes = Maps.newHashMap();

    final Iterator<Map.Entry<String, JsonNode>> iter = n.fields();

    while (iter.hasNext()) {
      final Map.Entry<String, JsonNode> e = iter.next();
      attributes.put(e.getKey(), e.getValue().asText());
    }

    return attributes;
  }

  private Map<String, String> decodeResources(JsonNode tree, String name) {
    final JsonNode n = tree.get(name);

    if (n == null) {
      return EMPTY_RESOURCES;
    }

    if (n.getNodeType() != JsonNodeType.OBJECT) {
      return EMPTY_RESOURCES;
    }

    final Map<String, String> resources = Maps.newHashMap();

    final Iterator<Map.Entry<String, JsonNode>> iter = n.fields();

    while (iter.hasNext()) {
      final Map.Entry<String, JsonNode> e = iter.next();
      resources.put(e.getKey(), e.getValue().asText());
    }

    return resources;
  }

  private Set<String> decodeTags(JsonNode tree, String name) {
    final JsonNode n = tree.get(name);

    if (n == null) {
      return EMPTY_TAGS;
    }

    if (n.getNodeType() != JsonNodeType.ARRAY) {
      return EMPTY_TAGS;
    }

    final List<String> tags = Lists.newArrayList();

    final Iterator<JsonNode> iter = n.elements();

    while (iter.hasNext()) {
      tags.add(iter.next().asText());
    }

    return Sets.newHashSet(tags);
  }
}
