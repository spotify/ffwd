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

package com.spotify.ffwd.debug;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.spotify.ffwd.model.v2.Batch;
import com.spotify.ffwd.model.v2.Metric;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFutureListener;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyDebugServer implements DebugServer {

  private static final Logger log = LoggerFactory.getLogger(NettyDebugServer.class);
  private static final String LINE_ENDING = "\n";
  private static final Charset UTF8 = Charset.forName("UTF-8");

  private final AtomicReference<Channel> server = new AtomicReference<>();

  private final InetSocketAddress localAddress;

  @Inject
  @Named("boss")
  private EventLoopGroup boss;

  @Inject
  @Named("worker")
  private EventLoopGroup worker;

  @Inject
  @Named("application/json")
  private ObjectMapper mapper;

  private final ChannelGroup connected = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

  public NettyDebugServer(InetSocketAddress localAddress) {
    this.localAddress = localAddress;
  }

  @Override
  public void inspectMetric(final String id, Metric metric) {
    if (connected.isEmpty()) {
      return;
    }

    try {
      sendInspectPacket(new WriteMetricEvent(id, metric));
    } catch (Exception e) {
      log.error("Failed to inspect metric {}", metric, e);
    }
  }

  @Override
  public void inspectBatch(final String id, final Batch batch) {
    if (connected.isEmpty()) {
      return;
    }

    try {
      sendInspectPacket(new WriteBatchEvent(id, batch));
    } catch (Exception e) {
      log.error("Failed to inspect batch {}", batch, e);
    }
  }


  private void sendInspectPacket(Object event) throws Exception {
    final byte[] buf = (mapper.writeValueAsString(event) + LINE_ENDING).getBytes(UTF8);
    final ChannelFuture cf =
        connected.iterator().next().writeAndFlush(Unpooled.wrappedBuffer(buf));
  }

  public CompletableFuture<Void> start() {
    CompletableFuture<Void> future = new CompletableFuture<>();

    final ServerBootstrap s = new ServerBootstrap();

    s.channel(NioServerSocketChannel.class);
    s.group(boss, worker);

    s.childHandler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(final Channel ch) {
        connected.add(ch);
        log.info("Connected {}", ch);

        ch.closeFuture().addListener((ChannelFutureListener) f -> {
          connected.remove(ch);
          log.info("Disconnected {}", ch);
        });
      }
    });

    s.bind(localAddress).addListener((ChannelFutureListener) f -> {
      if (!f.isSuccess()) {
        future.completeExceptionally(f.cause());
        return;
      }

      log.info("Bound to {}", localAddress);

      if (!server.compareAndSet(null, f.channel())) {
        f.channel().close();
        future.completeExceptionally(new IllegalStateException("server already started"));
        return;
      }

      future.complete(null);
    });

    return future;
  }

  public CompletableFuture<Void> stop() {
    final Channel server = this.server.getAndSet(null);

    if (server == null) {
      throw new IllegalStateException("server not started");
    }

    CompletableFuture<Void> serverClose = new CompletableFuture<>();

    server.close().addListener((ChannelFutureListener) f -> {
      if (!f.isSuccess()) {
        serverClose.completeExceptionally(f.cause());
        return;
      }

      serverClose.complete(null);
    });

    CompletableFuture<Void> channelGroupClose = new CompletableFuture<>();

    connected.close().addListener((ChannelGroupFutureListener) f -> {
      if (!f.isSuccess()) {
        channelGroupClose.completeExceptionally(f.cause());
        return;
      }

      channelGroupClose.complete(null);
    });

    return CompletableFuture.allOf(serverClose, channelGroupClose);
  }

  public String toString() {
    return "NettyDebugServer(localAddress=" + this.localAddress + ")";
  }

  @Data
  private static class WriteMetricEvent {

    private final String type = "metric";
    private final String id;
    private final Metric data;
  }

  @Data
  private static class WriteBatchEvent {

    private final String type = "batch";
    private final String id;
    private final Batch data;
  }
}
