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
package com.spotify.ffwd.protocol;

import com.google.inject.Inject;
import com.spotify.ffwd.input.PluginSource;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import eu.toolchain.async.Transform;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

public class ProtocolPluginSource implements PluginSource {
    @Inject
    private AsyncFramework async;

    @Inject
    private ProtocolServers servers;

    @Inject
    private Protocol protocol;

    @Inject
    private ProtocolServer server;

    @Inject
    private RetryPolicy retry;

    @Inject
    private Logger log;

    private final AtomicReference<ProtocolConnection> connection = new AtomicReference<>();

    @Override
    public void init() {
    }

    @Override
    public AsyncFuture<Void> start() {
        return servers
            .bind(log, protocol, server, retry)
            .transform(new Transform<ProtocolConnection, Void>() {
                @Override
                public Void transform(ProtocolConnection c) throws Exception {
                    if (!connection.compareAndSet(null, c)) {
                        c.stop();
                    }

                    return null;
                }
            });
    }

    @Override
    public AsyncFuture<Void> stop() {
        final ProtocolConnection c = connection.getAndSet(null);

        if (c == null) {
            return async.resolved(null);
        }

        return c.stop();
    }
}
