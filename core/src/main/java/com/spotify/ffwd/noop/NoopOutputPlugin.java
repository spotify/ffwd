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

package com.spotify.ffwd.noop;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.module.Batching;
import com.spotify.ffwd.output.OutputPlugin;
import com.spotify.ffwd.output.OutputPluginModule;
import com.spotify.ffwd.output.PluginSink;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class NoopOutputPlugin extends OutputPlugin {
    private static final long DEFAULT_FLUSH_INTERVAL =
        TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS);

    @JsonCreator
    public NoopOutputPlugin(
        @JsonProperty("flushInterval") Optional<Long> flushInterval,
        @JsonProperty("batching") Optional<Batching> batching,
        @JsonProperty("filter") Optional<Filter> filter
    ) {
        super(filter, Batching.from(flushInterval, batching, Optional.of(DEFAULT_FLUSH_INTERVAL)));
    }

    @Override
    public Module module(final Key<PluginSink> key, final String id) {
        return new OutputPluginModule(id) {
            @Override
            protected void configure() {
                final Key<NoopPluginSink> sinkKey =
                    Key.get(NoopPluginSink.class, Names.named("noopSink"));
                bind(sinkKey).to(NoopPluginSink.class).in(Scopes.SINGLETON);
                install(wrapPluginSink(sinkKey, key));
                expose(key);
            }
        };
    }
}
