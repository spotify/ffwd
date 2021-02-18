/*-
 * -\-\-
 * FastForward HTTP Module
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

package com.spotify.ffwd.http;

import com.google.inject.Inject;
import com.spotify.ffwd.input.PluginSource;
import com.spotify.ffwd.protocol.Protocol;
import com.spotify.ffwd.protocol.ProtocolConnection;
import com.spotify.ffwd.protocol.ProtocolServer;
import com.spotify.ffwd.protocol.ProtocolServers;
import com.spotify.ffwd.protocol.RetryPolicy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpPluginSource implements PluginSource {
  private static final Logger log = LoggerFactory.getLogger(HttpPluginSource.class);

  @Inject
  private ExecutorService executor;

  @Inject
  private ProtocolServers servers;

  @Inject
  private Protocol protocol;

  @Inject
  private ProtocolServer server;

  @Inject
  private RetryPolicy policy;

  private final AtomicReference<ProtocolConnection> connection = new AtomicReference<>();

  @Override
  public void init() {
  }

  @Override
  public CompletableFuture<Void> start() {
    return servers
        .bind(log, protocol, server, policy)
        .thenApplyAsync(c -> {
          if (!connection.compareAndSet(null, c)) {
            c.stop();
          }
          return null;
        }, executor);
  }

  @Override
  public CompletableFuture<Void> stop() {
    final ProtocolConnection c = connection.getAndSet(null);

    if (c == null) {
      return CompletableFuture.completedFuture(null);
    }

    return c.stop();
  }
}
