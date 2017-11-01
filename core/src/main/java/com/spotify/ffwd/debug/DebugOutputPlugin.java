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
package com.spotify.ffwd.debug;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.output.BatchedPluginSink;
import com.spotify.ffwd.output.OutputPlugin;
import com.spotify.ffwd.output.OutputPluginModule;
import com.spotify.ffwd.output.PluginSink;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class DebugOutputPlugin extends OutputPlugin {

    private static final long DEFAULT_FLUSH_INTERVAL =
        TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS);

    @JsonCreator
    public DebugOutputPlugin(
        @JsonProperty("flushInterval") Optional<Long> flushInterval,
        @JsonProperty("filter") Optional<Filter> filter
    ) {
        super(filter,
            flushInterval.isPresent() ? flushInterval : Optional.of(DEFAULT_FLUSH_INTERVAL));
    }

    @Override
    public Module module(final Key<PluginSink> key, final String id) {
        return new OutputPluginModule(id) {
            @Override
            protected void configure() {
                bind(BatchedPluginSink.class).to(DebugPluginSink.class);
                Key<PluginSink> sinkKey = Key.get(PluginSink.class, Names.named("debugSinkSink"));
                bind(sinkKey).to(DebugPluginSink.class);
                install(wrapPluginSink(sinkKey, key));
                expose(key);
            }
        };
    }

}
