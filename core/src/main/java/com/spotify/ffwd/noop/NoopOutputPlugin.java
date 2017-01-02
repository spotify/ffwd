/*
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
 */
package com.spotify.ffwd.noop;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.spotify.ffwd.output.BatchedPluginSink;
import com.spotify.ffwd.output.FlushingPluginSink;
import com.spotify.ffwd.output.OutputPlugin;
import com.spotify.ffwd.output.OutputPluginModule;
import com.spotify.ffwd.output.PluginSink;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class NoopOutputPlugin implements OutputPlugin {
    private static final long DEFAULT_FLUSH_INTERVAL =
        TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS);

    private final Long flushInterval;

    @JsonCreator
    public NoopOutputPlugin(@JsonProperty("flushInterval") Long flushInterval) {
        this.flushInterval = Optional.ofNullable(flushInterval).orElse(DEFAULT_FLUSH_INTERVAL);
    }

    @Override
    public Module module(final Key<PluginSink> key, final String id) {
        return new OutputPluginModule(id) {
            @Override
            protected void configure() {
                if (flushInterval != null) {
                    bind(BatchedPluginSink.class).to(NoopPluginSink.class).in(Scopes.SINGLETON);
                    bind(key).toInstance(new FlushingPluginSink(flushInterval));
                } else {
                    bind(key).to(NoopPluginSink.class).in(Scopes.SINGLETON);
                }

                expose(key);
            }
        };
    }

    @Override
    public String id(int index) {
        return Integer.toString(index);
    }
}
