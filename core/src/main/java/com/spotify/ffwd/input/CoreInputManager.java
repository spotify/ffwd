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
package com.spotify.ffwd.input;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.spotify.ffwd.debug.DebugServer;
import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.output.OutputManager;

import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import eu.toolchain.async.Collector;

/**
 * Responsible for receiving, logging and transforming the event.
 *
 * @author udoprog
 */
public class CoreInputManager implements InputManager {
    private static final String DEBUG_ID = "core.input";

    @Inject
    private List<PluginSource> sources;

    @Inject
    private AsyncFramework async;

    @Inject
    private OutputManager output;

    @Inject
    private DebugServer debug;

    @Override
    public void init() {
        for (final PluginSource s : sources)
            s.init();
    }

    @Override
    public void receiveEvent(Event event) {
        debug.inspectEvent(DEBUG_ID, event);
        output.sendEvent(event);
    }

    @Override
    public void receiveMetric(Metric metric) {
        debug.inspectMetric(DEBUG_ID, metric);
        output.sendMetric(metric);
    }

    @Override
    public AsyncFuture<Void> start() {
        final ArrayList<AsyncFuture<Void>> futures = Lists.newArrayList();

        for (final PluginSource s : sources)
            futures.add(s.start());

        return async.collect(futures, new Collector<Void, Void>() {
            @Override
            public Void collect(Collection<Void> results) throws Exception {
                return null;
            }
        });
    }

    @Override
    public AsyncFuture<Void> stop() {
        final ArrayList<AsyncFuture<Void>> futures = Lists.newArrayList();

        for (final PluginSource s : sources)
            futures.add(s.stop());

        return async.collect(futures, new Collector<Void, Void>() {
            @Override
            public Void collect(Collection<Void> results) throws Exception {
                return null;
            }
        });
    }
}
