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
package com.spotify.ffwd.generated;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.spotify.ffwd.input.InputManager;
import com.spotify.ffwd.input.PluginSource;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.output.OutputManager;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GeneratedPluginSource implements PluginSource {
    @Inject
    private AsyncFramework async;

    @Inject
    private InputManager input;
    @Inject
    private OutputManager output;

    private final int count = 10000;

    private volatile AsyncFuture<Void> task;
    private volatile List<Metric> metrics;
    private volatile boolean stopped = false;

    private final Random random = new Random();
    private final ExecutorService single = Executors.newSingleThreadExecutor();

    private final boolean sameHost;

    public GeneratedPluginSource(boolean sameHost) {
        this.sameHost = sameHost;
    }

    @Override
    public void init() {
        task = async.call(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                generate();
                return null;
            }
        }, single);
    }

    @Override
    public AsyncFuture<Void> start() {
        return async.call(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                metrics = generateMetrics();
                return null;
            }
        });
    }

    private List<Metric> generateMetrics() {
        final List<Metric> metrics = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            final String key = "generated";
            final double value = 0.42 * i;
            final Date time = null;
            final String host = generateHost(i);
            final Set<String> riemannTags = ImmutableSet.of();
            final Map<String, String> tags = ImmutableMap.of("what", "metric-" + i);
            final Map<String, String> resource = ImmutableMap.of();
            final String proc = null;

            tags.put("host", host);

            metrics.add(new Metric(key, value, time, riemannTags, tags, resource, proc));
        }

        return metrics;
    }

    private String generateHost(int i) {
        if (sameHost) {
            return "host";
        }

        return "host" + i;
    }

    @Override
    public AsyncFuture<Void> stop() {
        stopped = true;
        return task;
    }

    private void generate() throws InterruptedException {
        while (!stopped) {
            input.receiveMetric(randomMetric());
            Thread.sleep(10);
        }
    }

    private Metric randomMetric() {
        return metrics.get(random.nextInt(metrics.size()));
    }
}
