/*-
 * -\-\-
 * FastForward Agent
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

package com.spotify.ffwd;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.inject.Injector;
import com.spotify.ffwd.output.BatchingPluginSink;
import com.spotify.ffwd.output.CoreOutputManager;
import com.spotify.ffwd.output.FilteringPluginSink;
import com.spotify.ffwd.output.OutputManager;
import com.spotify.ffwd.output.PluginSink;
import eu.toolchain.async.AsyncFramework;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FfwdConfigurationTest {

    private AsyncFramework async;

    @Before
    public void setup() {
    }

    @Test
    public void testConfAllPluginsEnabled() throws IOException {
        final List<List<String>> expectedSinks = ImmutableList.of(
            ImmutableList.of("com.spotify.ffwd.output.FilteringPluginSink",
                "com.spotify.ffwd.output.BatchingPluginSink",
                "com.spotify.ffwd.debug.DebugPluginSink"),
            ImmutableList.of("com.spotify.ffwd.output.FilteringPluginSink",
                "com.spotify.ffwd.output.BatchingPluginSink",
                "com.spotify.ffwd.http.HttpPluginSink"),
            ImmutableList.of("com.spotify.ffwd.output.FilteringPluginSink",
                "com.spotify.ffwd.output.BatchingPluginSink",
                "com.spotify.ffwd.kafka.KafkaPluginSink"),
            ImmutableList.of("com.spotify.ffwd.output.FilteringPluginSink",
                "com.spotify.ffwd.output.BatchingPluginSink",
                "com.spotify.ffwd.protocol.ProtocolPluginSink"),
            ImmutableList.of("com.spotify.ffwd.output.FilteringPluginSink",
                "com.spotify.ffwd.output.BatchingPluginSink",
                "com.spotify.ffwd.signalfx.SignalFxPluginSink"));

        verifyLoadedSinksForConfig(
            "List of sink chains match the expectation, with batch and filter for each sink",
            "ffwd-all-plugins.yaml", expectedSinks);
    }

    @Test
    public void testConfMixedPluginsEnabled() throws IOException {
        final List<List<String>> expectedSinks = ImmutableList.of(
            ImmutableList.of("com.spotify.ffwd.output.FilteringPluginSink",
                "com.spotify.ffwd.output.BatchingPluginSink",
                "com.spotify.ffwd.debug.DebugPluginSink"),
            ImmutableList.of("com.spotify.ffwd.output.BatchingPluginSink",
                "com.spotify.ffwd.http.HttpPluginSink"),
            ImmutableList.of("com.spotify.ffwd.kafka.KafkaPluginSink"),
            ImmutableList.of("com.spotify.ffwd.output.FilteringPluginSink",
                "com.spotify.ffwd.protocol.ProtocolPluginSink"),
            ImmutableList.of("com.spotify.ffwd.output.FilteringPluginSink",
                "com.spotify.ffwd.output.BatchingPluginSink",
                "com.spotify.ffwd.signalfx.SignalFxPluginSink"));

        verifyLoadedSinksForConfig(
            "List of sink chains match the expectation, with batch and/or filter for some",
            "ffwd-mixed-plugins.yaml", expectedSinks);
    }

    private void verifyLoadedSinksForConfig(
        final String expectationString, final String configName,
        final List<List<String>> expectedSinks
    ) {
        final InputStream configStream = stream(configName).get();

        final FastForwardAgent agent =
            FastForwardAgent.setup(Optional.empty(), Optional.of(configStream));
        final Injector primaryInjector = agent.getCore().getPrimaryInjector();
        final CoreOutputManager outputManager =
            (CoreOutputManager) primaryInjector.getInstance(OutputManager.class);
        final List<PluginSink> sinks = outputManager.getSinks();

        final List<List<String>> sinkChains = new ArrayList<>();

        for (final PluginSink sink : sinks) {
            sinkChains.add(extractSinkChain(sink));
        }

        assertEquals(expectationString, expectedSinks, sinkChains);
    }

    private List<String> extractSinkChain(final PluginSink sink) {
        final List<String> sinkChain = new ArrayList<>();

        sinkChain.add(sink.getClass().getCanonicalName());

        if (sink instanceof BatchingPluginSink) {
            final BatchingPluginSink batching = (BatchingPluginSink) sink;
            sinkChain.addAll(extractSinkChain(batching.getSink()));
        }

        if (sink instanceof FilteringPluginSink) {
            final FilteringPluginSink filtering = (FilteringPluginSink) sink;
            sinkChain.addAll(extractSinkChain(filtering.getSink()));
        }

        return sinkChain;
    }

    private Supplier<InputStream> stream(String name) {
        return () -> getClass().getClassLoader().getResourceAsStream(name);
    }
}
