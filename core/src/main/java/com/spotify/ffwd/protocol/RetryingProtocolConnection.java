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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.Timer;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

public class RetryingProtocolConnection implements ProtocolConnection {

  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private final AtomicReference<Channel> channel = new AtomicReference<>();
  private final Object lock = new Object();

  private final Timer timer;
  private final Logger log;
  private final RetryPolicy policy;
  private final ProtocolChannelSetup action;

  private final CompletableFuture<ProtocolConnection> initialFuture;

  RetryingProtocolConnection(
      Timer timer, Logger log, RetryPolicy policy, ProtocolChannelSetup action
  ) {
    this.timer = timer;
    this.log = log;
    this.policy = policy;
    this.action = action;

    this.initialFuture = new CompletableFuture<>();

    trySetup(0);
  }

  private void trySetup(final int attempt) {
    log.info("Attempt {}", action);

    final ChannelFuture connect = action.setup();

    connect.addListener((ChannelFutureListener) future -> {
      if (future.isSuccess()) {
        log.info("Successful {}", action);
        setChannel(future.channel());
        return;
      }

      final long delay = policy.delay(attempt);

      log.warn("Failed {} (attempt: {}), retrying in {}s: {}", action, attempt + 1,
          TimeUnit.SECONDS.convert(delay, TimeUnit.MILLISECONDS),
          future.cause().getMessage());

      timer.newTimeout(timeout -> {
        if (stopped.get()) {
          return;
        }

        trySetup(attempt + 1);
      }, delay, TimeUnit.MILLISECONDS);
    });
  }

  /**
   * Successfully connected, set channel to indicate that we are connected.
   */
  private void setChannel(Channel c) {
    synchronized (lock) {
      if (stopped.get()) {
        c.close();
        return;
      }

      if (!initialFuture.isDone()) {
        initialFuture.complete(this);
      }

      channel.set(c);
    }

    c.closeFuture().addListener((ChannelFutureListener) future -> {
      log.info("Lost {}, retrying", action);
      channel.set(null);
      trySetup(0);
    });
  }

  @Override
  public CompletableFuture<Void> stop() {
    final Channel c;

    synchronized (lock) {
      stopped.set(true);

      c = channel.getAndSet(null);

      if (c == null) {
        return CompletableFuture.completedFuture(null);
      }
    }

    CompletableFuture<Void> future = new CompletableFuture<>();

    c.close().addListener((ChannelFutureListener) f -> {
      try {
        future.complete(f.get());
      } catch (ExecutionException e) {
        future.completeExceptionally(e);
      }
    });

    return future;
  }

  @Override
  public void send(Object message) {
    final Channel c = channel.get();

    if (c == null) {
      return;
    }

    c.writeAndFlush(message).addListener((ChannelFutureListener) future -> {
      if (!future.isSuccess()) {
        log.error("failed to send metric", future.cause());
      }
    });
  }

  @Override
  public CompletableFuture<Void> sendAll(Collection<? extends Object> batch) {
    final Channel c = channel.get();

    if (c == null) {
      return CompletableFuture.failedFuture(new IllegalStateException("not connected"));
    }

    CompletableFuture<Void> future = new CompletableFuture<>();

    c.writeAndFlush(batch).addListener((ChannelFutureListener) f -> {
      try {
        future.complete(f.get());
      } catch (ExecutionException e) {
        future.completeExceptionally(e);
      }
    });

    return future;
  }

  @Override
  public boolean isConnected() {
    final Channel c = channel.get();

    if (c == null) {
      return false;
    }

    return c.isActive();
  }

  /**
   * Return a future that will be resolved when an initial action has been successful.
   */
  public CompletableFuture<ProtocolConnection> getInitialFuture() {
    return initialFuture;
  }
}
