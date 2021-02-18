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

package com.spotify.ffwd.protocol;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.Timer;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public class ProtocolClientsImpl implements ProtocolClients {

  @Inject
  @Named("worker")
  private EventLoopGroup worker;

  @Inject
  private Timer timer;

  @Override
  public CompletableFuture<ProtocolConnection> connect(
      Logger log, Protocol protocol, ProtocolClient client, RetryPolicy policy
  ) {
    if (protocol.getType() == ProtocolType.UDP) {
      return connectUDP(protocol, client, policy);
    }

    if (protocol.getType() == ProtocolType.TCP) {
      return connectTCP(log, protocol, client, policy);
    }

    throw new IllegalArgumentException("Unsupported protocol: " + protocol);
  }

  private CompletableFuture<ProtocolConnection> connectTCP(
      Logger log, Protocol protocol, ProtocolClient client, RetryPolicy policy
  ) {
    final Bootstrap b = new Bootstrap();

    b.group(worker);
    b.channel(NioSocketChannel.class);
    b.handler(client.initializer());

    b.option(ChannelOption.SO_KEEPALIVE, true);

    final String host = protocol.getAddress().getHostString();
    final int port = protocol.getAddress().getPort();

    final ProtocolConnection connection =
        new RetryingProtocolConnection(timer, log, policy, new ProtocolChannelSetup() {
          @Override
          public ChannelFuture setup() {
            return b.connect(host, port);
          }

          @Override
          public String toString() {
            return String.format("connect tcp://%s:%d", host, port);
          }
        });

    return CompletableFuture.completedFuture(connection);
  }

  private CompletableFuture<ProtocolConnection> connectUDP(
      Protocol protocol, ProtocolClient client, RetryPolicy policy
  ) {
    return CompletableFuture.failedFuture(new RuntimeException("not implemented"));
  }
}
