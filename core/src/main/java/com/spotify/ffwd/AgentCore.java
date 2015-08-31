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
package com.spotify.ffwd;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.spotify.ffwd.debug.DebugServer;
import com.spotify.ffwd.debug.NettyDebugServer;
import com.spotify.ffwd.debug.NoopDebugServer;
import com.spotify.ffwd.filter.AndFilter;
import com.spotify.ffwd.filter.Filter;
import com.spotify.ffwd.filter.FilterDeserializer;
import com.spotify.ffwd.filter.MatchKey;
import com.spotify.ffwd.filter.MatchTag;
import com.spotify.ffwd.filter.NotFilter;
import com.spotify.ffwd.filter.OrFilter;
import com.spotify.ffwd.filter.TrueFilter;
import com.spotify.ffwd.input.InputManager;
import com.spotify.ffwd.module.FastForwardModule;
import com.spotify.ffwd.module.PluginContext;
import com.spotify.ffwd.module.PluginContextImpl;
import com.spotify.ffwd.output.OutputManager;
import com.spotify.ffwd.protocol.ProtocolClients;
import com.spotify.ffwd.protocol.ProtocolClientsImpl;
import com.spotify.ffwd.protocol.ProtocolServers;
import com.spotify.ffwd.protocol.ProtocolServersImpl;
import com.spotify.ffwd.serializer.Serializer;
import com.spotify.ffwd.serializer.ToStringSerializer;
import com.spotify.ffwd.statistics.CoreStatistics;
import com.spotify.ffwd.statistics.NoopCoreStatistics;

import eu.toolchain.async.AsyncCaller;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import eu.toolchain.async.DirectAsyncCaller;
import eu.toolchain.async.TinyAsync;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AgentCore {
    private final List<Class<? extends FastForwardModule>> modules;
    private final Path config;
    private final CoreStatistics statistics;

    private AgentCore(final List<Class<? extends FastForwardModule>> modules, Path config, CoreStatistics statistics) {
        this.modules = modules;
        this.config = config;
        this.statistics = statistics;
    }

    public void run() throws Exception {
        final Injector early = setupEarlyInjector();
        final AgentConfig config = readConfig(early);
        final Injector primary = setupPrimaryInjector(early, config);

        start(primary);
        log.info("Started!");

        waitUntilStopped(primary);
        log.info("Stopped, Bye Bye!");
    }

    private void waitUntilStopped(final Injector primary) throws InterruptedException {
        final CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(setupShutdownHook(primary, shutdown));
        shutdown.await();
    }

    private Thread setupShutdownHook(final Injector primary, final CountDownLatch shutdown) {
        final Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    AgentCore.this.stop(primary);
                } catch (Exception e) {
                    log.error("AgentCore#stop(Injector) failed", e);
                }

                shutdown.countDown();
            }
        };

        thread.setName("ffwd-agent-core-shutdown-hook");

        return thread;
    }

    private void start(final Injector primary) throws Exception, InterruptedException, ExecutionException {
        final InputManager input = primary.getInstance(InputManager.class);
        final OutputManager output = primary.getInstance(OutputManager.class);
        final DebugServer debug = primary.getInstance(DebugServer.class);

        final AsyncFramework async = primary.getInstance(AsyncFramework.class);
        final ArrayList<AsyncFuture<Void>> startup = Lists.newArrayList();

        log.info("Waiting for all components to start...");

        startup.add(output.start());
        startup.add(input.start());
        startup.add(debug.start());

        async.collectAndDiscard(startup).get(10, TimeUnit.SECONDS);

        input.init();
        output.init();
    }

    private void stop(final Injector primary) throws Exception {
        final InputManager input = primary.getInstance(InputManager.class);
        final OutputManager output = primary.getInstance(OutputManager.class);
        final DebugServer debug = primary.getInstance(DebugServer.class);

        final AsyncFramework async = primary.getInstance(AsyncFramework.class);
        final ArrayList<AsyncFuture<Void>> shutdown = Lists.newArrayList();

        log.info("Waiting for all components to stop...");

        shutdown.add(input.stop());
        shutdown.add(output.stop());
        shutdown.add(debug.stop());

        async.collectAndDiscard(shutdown).get();
    }

    /**
     * Setup early application Injector.
     *
     * The early injector is used by modules to configure the system.
     *
     * @throws Exception If something could not be set up.
     */
    private Injector setupEarlyInjector() throws Exception {
        final List<Module> modules = Lists.newArrayList();

        modules.add(new AbstractModule() {
            @Singleton
            @Provides
            public Map<String, FilterDeserializer.PartialDeserializer> filters() {
                final Map<String, FilterDeserializer.PartialDeserializer> filters = new HashMap<>();
                filters.put("key", new MatchKey.Deserializer());
                filters.put("=", new MatchTag.Deserializer());
                filters.put("true", new TrueFilter.Deserializer());
                filters.put("false", new TrueFilter.Deserializer());
                filters.put("and", new AndFilter.Deserializer());
                filters.put("or", new OrFilter.Deserializer());
                filters.put("not", new NotFilter.Deserializer());
                return filters;
            }

            @Singleton
            @Provides
            @Named("application/yaml+config")
            public SimpleModule configModule(Map<String, FilterDeserializer.PartialDeserializer> filters) {
                final SimpleModule module = new SimpleModule();
                module.addDeserializer(Filter.class, new FilterDeserializer(filters));
                return module;
            }

            @Override
            protected void configure() {
                bind(PluginContext.class).toInstance(new PluginContextImpl());
            }
        });

        final Injector injector = Guice.createInjector(modules);

        for (final FastForwardModule m : loadModules(injector)) {
            log.info("Setting up {}", m);

            try {
                m.setup();
            } catch (Exception e) {
                throw new Exception("Failed to call #setup() for module: " + m, e);
            }
        }

        return injector;
    }

    /**
     * Setup primary Injector.
     *
     * @return The primary injector.
     */
    private Injector setupPrimaryInjector(final Injector early, final AgentConfig config) {
        final List<Module> modules = Lists.newArrayList();

        modules.add(new AbstractModule() {
            @Override
            protected void configure() {
                if (config.getDebug().isPresent()) {
                    final AgentConfig.Debug debug = config.getDebug().get();
                    bind(DebugServer.class).toInstance(new NettyDebugServer(debug.getLocalAddress()));
                } else {
                    bind(DebugServer.class).toInstance(new NoopDebugServer());
                }
            }
        });

        modules.add(new AbstractModule() {
            @Singleton
            @Provides
            private CoreStatistics statistics() {
                return statistics;
            }

            @Singleton
            @Provides
            private ExecutorService executor() {
                final ThreadFactory factory = new ThreadFactoryBuilder().setNameFormat("ffwd-async-%d").build();
                return Executors.newFixedThreadPool(config.getAsyncThreads(), factory);
            }

            @Singleton
            @Provides
            private ScheduledExecutorService scheduledExecutor() {
                final ThreadFactory factory = new ThreadFactoryBuilder()
                        .setNameFormat("ffwd-scheduler-%d").build();
                return Executors.newScheduledThreadPool(config.getSchedulerThreads(), factory);
            }

            @Singleton
            @Provides
            private AsyncFramework async(ExecutorService executor) {
                final AsyncCaller caller = new DirectAsyncCaller() {
                    @Override
                    protected void internalError(String what, Throwable e) {
                        log.error("Async call '{}' failed", what, e);
                    }
                };

                return TinyAsync.builder().executor(executor).caller(caller).build();
            }

            @Singleton
            @Provides
            @Named("boss")
            public EventLoopGroup bosses() {
                final ThreadFactory factory = new ThreadFactoryBuilder().setNameFormat("ffwd-boss-%d").build();
                return new NioEventLoopGroup(config.getBossThreads(), factory);
            }

            @Singleton
            @Provides
            @Named("worker")
            public EventLoopGroup workers() {
                final ThreadFactory factory = new ThreadFactoryBuilder().setNameFormat("ffwd-worker-%d").build();
                return new NioEventLoopGroup(config.getWorkerThreads(), factory);
            }

            @Singleton
            @Provides
            @Named("application/json")
            public ObjectMapper jsonMapper() {
                return new ObjectMapper();
            }

            @Singleton
            @Provides
            public AgentConfig config() {
                return config;
            }

            @Override
            protected void configure() {
                bind(Key.get(Serializer.class, Names.named("default"))).to(ToStringSerializer.class).in(
                        Scopes.SINGLETON);
                bind(Timer.class).to(HashedWheelTimer.class).in(Scopes.SINGLETON);
                bind(ProtocolServers.class).to(ProtocolServersImpl.class).in(Scopes.SINGLETON);
                bind(ProtocolClients.class).to(ProtocolClientsImpl.class).in(Scopes.SINGLETON);
            }
        });

        modules.add(config.getInput().module());
        modules.add(config.getOutput().module());

        return early.createChildInjector(modules);
    }

    private AgentConfig readConfig(Injector early) throws IOException {
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        final SimpleModule module = early
                .getInstance(Key.get(SimpleModule.class, Names.named("application/yaml+config")));

        mapper.registerModule(module);

        try (final InputStream input = Files.newInputStream(this.config)) {
            return mapper.readValue(input, AgentConfig.class);
        } catch (JsonParseException e) {
            throw new IOException("Failed to parse configuration", e);
        }
    }

    private List<FastForwardModule> loadModules(Injector injector) throws Exception {
        final List<FastForwardModule> modules = Lists.newArrayList();

        for (final Class<? extends FastForwardModule> module : this.modules) {
            final Constructor<? extends FastForwardModule> constructor;

            try {
                constructor = module.getConstructor();
            } catch (NoSuchMethodException e) {
                throw new Exception("Expected empty constructor for class: " + module, e);
            }

            final FastForwardModule m;

            try {
                m = constructor.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new Exception("Failed to call constructor for class: " + module, e);
            }

            injector.injectMembers(m);
            modules.add(m);
        }

        return modules;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<Class<? extends FastForwardModule>> modules = Lists.newArrayList();
        private Path config = Paths.get("ffwd.yaml");
        private CoreStatistics statistics = NoopCoreStatistics.get();

        public Builder config(Path config) {
            if (config == null)
                throw new NullPointerException("'config' must not be null");

            this.config = config;
            return this;
        }

        public Builder modules(List<Class<? extends FastForwardModule>> modules) {
            if (modules == null)
                throw new NullPointerException("'modules' must not be null");

            this.modules = modules;
            return this;
        }

        public Builder statistics(CoreStatistics statistics) {
            if (statistics == null)
                throw new NullPointerException("'statistics' most not be null");

            this.statistics = statistics;
            return this;
        }

        public AgentCore build() {
            return new AgentCore(modules, config, statistics);
        }
    }
}
