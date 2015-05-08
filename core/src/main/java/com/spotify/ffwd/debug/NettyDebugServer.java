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
package com.spotify.ffwd.debug;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.ChannelGroupFutureListener;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicReference;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;

import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import eu.toolchain.async.ResolvableFuture;

@Slf4j
@RequiredArgsConstructor
@ToString(of = { "localAddress" })
public class NettyDebugServer implements DebugServer {
    private static final String LINE_ENDING = "\n";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    final AtomicReference<Channel> server = new AtomicReference<>();

    private final InetSocketAddress localAddress;

    @Inject
    private AsyncFramework async;

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

    public void inspectEvent(final String id, Event event) {
        if (connected.isEmpty())
            return;

        try {
            sendInspectPacket(new WriteEventEvent(id, event));
        } catch (Exception e) {
            log.error("Failed to inspect event {}", event, e);
        }
    }

    public void inspectMetric(final String id, Metric metric) {
        if (connected.isEmpty())
            return;

        try {
            sendInspectPacket(new WriteMetricEvent(id, metric));
        } catch (Exception e) {
            log.error("Failed to inspect metric {}", metric, e);
        }
    }

    private void sendInspectPacket(Object event) throws Exception {
        final byte[] buf = (mapper.writeValueAsString(event) + LINE_ENDING).getBytes(UTF8);
        final ChannelFuture cf = connected.iterator().next().writeAndFlush(Unpooled.wrappedBuffer(buf));
    }

    public AsyncFuture<Void> start() {
        final ResolvableFuture<Void> future = async.future();

        final ServerBootstrap s = new ServerBootstrap();

        s.channel(NioServerSocketChannel.class);
        s.group(boss, worker);

        s.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(final Channel ch) throws Exception {
                connected.add(ch);
                log.info("Connected {}", ch);

                ch.closeFuture().addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        connected.remove(ch);
                        log.info("Disconnected {}", ch);
                    }
                });
            }
        });

        s.bind(localAddress).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture f) throws Exception {
                if (!f.isSuccess()) {
                    future.fail(f.cause());
                    return;
                }

                log.info("Bound to {}", localAddress);

                if (!server.compareAndSet(null, f.channel())) {
                    f.channel().close();
                    future.fail(new IllegalStateException("server already started"));
                    return;
                }

                future.resolve(null);
            }
        });

        return future;
    }

    public AsyncFuture<Void> stop() {
        final Channel server = this.server.getAndSet(null);

        if (server == null)
            throw new IllegalStateException("server not started");

        final ResolvableFuture<Void> serverClose = async.future();

        server.close().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture f) throws Exception {
                if (!f.isSuccess()) {
                    serverClose.fail(f.cause());
                    return;
                }

                serverClose.resolve(null);
            }
        });

        final ResolvableFuture<Void> channelGroupClose = async.future();

        connected.close().addListener(new ChannelGroupFutureListener() {
            @Override
            public void operationComplete(ChannelGroupFuture f) throws Exception {
                if (!f.isSuccess()) {
                    channelGroupClose.fail(f.cause());
                    return;
                }

                channelGroupClose.resolve(null);
            }
        });

        return async.collectAndDiscard(ImmutableList.<AsyncFuture<Void>> of(serverClose, channelGroupClose));
    }

    @Data
    public static class WriteMetricEvent {
        private final String type = "metric";
        private final String id;
        private final Metric data;
    }

    @Data
    public static class WriteEventEvent {
        private final String type = "event";
        private final String id;
        private final Event data;
    }
}