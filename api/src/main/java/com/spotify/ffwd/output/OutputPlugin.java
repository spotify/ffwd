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
package com.spotify.ffwd.output;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.name.Names;
import com.spotify.ffwd.filter.Filter;
import java.util.Optional;

@JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
public abstract class OutputPlugin {

    protected final Optional<Long> flushInterval;
    protected final Optional<Filter> filter;

    public OutputPlugin() {
        filter = Optional.empty();
        flushInterval = Optional.empty();
    }

    public OutputPlugin(
        final Optional<Filter> filter, final Optional<Long> flushInterval
    ) {
        this.filter = filter;
        this.flushInterval = flushInterval;
    }

    /**
     * This method allows to wrap plugin sink implementation type depending on configuration.
     * If no additional configuration is specified then subtype of
     * com.spotify.ffwd.output.BatchedPluginSink will be bound per plugin.
     *
     * <code>output := new SubtypeOfBatchedPluginSink()</code>
     *
     * If 'flushInterval' key is specified, then corresponding subtype of
     * com.spotify.ffwd.output.BatchedPluginSink will be wrapped into
     * com.spotify.ffwd.output.FlushingPluginSink:
     *
     * <code>output := new FlushingPluginSink(flushInterval, delegator:=output)</code>
     *
     * The resulting plugin sink type may be further wrapped into
     * com.spotify.ffwd.output.FilteringPluginSink type if 'filter' key is specified
     * in plugin configuration:
     *
     * <code>output := new FilteringPluginSink(filter, delegator:=output)</code>
     *
     * @param  input   binding key with injection type of plugin sink
     * @param  output  binding key, containing injection type of wrapping plugin sink
     * @return module that exposes output binding key
     */
    protected Module wrapPluginSink(
        final Key<? extends PluginSink> input, final Key<PluginSink> output
    ) {
        return new PrivateModule() {
            @Override
            protected void configure() {
                Key<PluginSink> sinkKey = (Key<PluginSink>) input;

                if (flushInterval != null && flushInterval.isPresent() &&
                    BatchedPluginSink.class.isAssignableFrom(
                        sinkKey.getTypeLiteral().getRawType())) {
                    final Key<PluginSink> flushingKey =
                        Key.get(PluginSink.class, Names.named("flushing"));
                    install(new OutputDelegatingModule<>(sinkKey, flushingKey,
                        new FlushingPluginSink(flushInterval.get())));
                    sinkKey = flushingKey;
                }

                if (filter != null && filter.isPresent()) {
                    final Key<PluginSink> filteringKey =
                        Key.get(PluginSink.class, Names.named("filtered"));
                    install(new OutputDelegatingModule<>(sinkKey, filteringKey,
                        new FilteringPluginSink(filter.get())));
                    sinkKey = filteringKey;
                }

                bind(output).to(sinkKey);
                expose(output);
            }
        };
    }

    public abstract Module module(Key<PluginSink> key, String id);
}
