// $LICENSE
/**
 * Copyright 2013-2014 Spotify AB. All rights reserved.
 *
 * The contents of this file are licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 **/
package com.spotify.ffwd;

import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.spotify.ffwd.module.FastForwardModule;

@Slf4j
public class FastForwardAgent {
    public static void main(String argv[]) {
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                log.error("Uncaught exception in thread {}, exiting (status = 2)", t.getName(), e);
                System.exit(2);
            }
        });

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

        final AgentCore.Builder builder = AgentCore.builder().modules(modules);

        if (argv.length > 0)
            builder.config(Paths.get(argv[0]));

        final AgentCore core = builder.build();

        try {
            core.run();
        } catch (Exception e) {
            log.error("Error in agent, exiting", e);
            System.exit(1);
            return;
        }

        System.exit(0);
    }
}
