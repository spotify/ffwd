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
package com.spotify.ffwd;

import com.spotify.ffwd.module.FastForwardModule;
import com.spotify.ffwd.statistics.SemanticCoreStatistics;
import com.spotify.metrics.core.MetricId;
import com.spotify.metrics.core.SemanticMetricRegistry;
import com.spotify.metrics.ffwd.FastForwardReporter;
import com.spotify.metrics.jvm.GarbageCollectorMetricSet;
import com.spotify.metrics.jvm.MemoryUsageGaugeSet;
import com.spotify.metrics.jvm.ThreadStatesMetricSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class FastForwardAgent {
    public static void main(String[] argv) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            log.error("Uncaught exception in thread {}, exiting (status = 2)", thread.getName(),
                throwable);
            System.exit(2);
        });

        final Statistics s;

        try {
            s = setupStatistics();
        } catch (Exception e) {
            log.error("Failed to setup statistics", e);
            System.exit(1);
            return;
        }

        final List<Class<? extends FastForwardModule>> modules = new ArrayList<>();

        // built-in core
        modules.add(com.spotify.ffwd.debug.DebugModule.class);
        modules.add(com.spotify.ffwd.json.JsonModule.class);
        modules.add(com.spotify.ffwd.protobuf.ProtobufModule.class);
        modules.add(com.spotify.ffwd.serializer.BuiltInSerializers.class);
        modules.add(com.spotify.ffwd.noop.NoopModule.class);
        modules.add(com.spotify.ffwd.generated.GeneratedModule.class);

        // additional
        modules.add(com.spotify.ffwd.kafka.KafkaModule.class);
        modules.add(com.spotify.ffwd.riemann.RiemannModule.class);
        modules.add(com.spotify.ffwd.carbon.CarbonModule.class);
        modules.add(com.spotify.ffwd.template.TemplateOutputModule.class);
        modules.add(com.spotify.ffwd.signalfx.SignalFxModule.class);

        final AgentCore.Builder builder = AgentCore.builder().modules(modules);

        if (argv.length > 0) {
            builder.config(Paths.get(argv[0]));
        }

        builder.statistics(s.statistics);

        final AgentCore core = builder.build();

        try {
            core.run();
        } catch (Exception e) {
            log.error("Error in agent, exiting", e);
            System.exit(1);
            return;
        }

        s.stop();
        System.exit(0);
    }

    private static Statistics setupStatistics() throws IOException {
        final SemanticMetricRegistry registry = new SemanticMetricRegistry();
        final SemanticCoreStatistics statistics = new SemanticCoreStatistics(registry);

        final MetricId gauges = MetricId.build();

        registry.register(gauges, new ThreadStatesMetricSet());
        registry.register(gauges, new GarbageCollectorMetricSet());
        registry.register(gauges, new MemoryUsageGaugeSet());

        final MetricId metric = MetricId.build("ffwd-java");

        final FastForwardReporter ffwd = FastForwardReporter
            .forRegistry(registry)
            .schedule(TimeUnit.SECONDS, 30)
            .prefix(metric)
            .build();

        ffwd.start();

        return new Statistics(ffwd, statistics);
    }

    @RequiredArgsConstructor
    private static class Statistics {
        private final FastForwardReporter ffwd;
        private final SemanticCoreStatistics statistics;

        void stop() {
            ffwd.stop();
        }
    }
}
