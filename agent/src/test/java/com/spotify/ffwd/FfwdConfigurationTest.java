/*-
 * -\-\-
 * FastForward Agent
 * --
 * Copyright (C) 2016 - 2019 Spotify AB
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
import com.spotify.ffwd.output.BatchingPluginSink;
import com.spotify.ffwd.output.CoreOutputManager;
import com.spotify.ffwd.output.FilteringPluginSink;
import com.spotify.ffwd.output.OutputManager;
import com.spotify.ffwd.output.PluginSink;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FfwdConfigurationTest {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void testConfAllPluginsEnabled() {
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
    public void testConfMixedPluginsEnabled() {
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

    @Test
    public void testConfigFromEnvVars() {
        environmentVariables.set("FFWD_TTL", "100");
        CoreOutputManager outputManager = getOutputManager(null);
        assertEquals(100, outputManager.getTtl());
    }

    @Test
    public void testIgnoreUnknownFields() {
        Path configPath = resource("invalid.yaml");

        String host = getOutputManager(configPath).getHost();
        assertEquals("jimjam", host);
    }

    @Test
    public void testMergeOrder() {
        environmentVariables.set("FFWD_TTL", "100");
        Path configPath = resource("basic-settings.yaml");
        CoreOutputManager outputManager = getOutputManager(configPath);

        assertEquals(100, outputManager.getTtl());
        assertEquals("jimjam", outputManager.getHost());
    }

    private CoreOutputManager getOutputManager(final Path configPath) {
        final FastForwardAgent agent = FastForwardAgent.setup(Optional.ofNullable(configPath));
        return (CoreOutputManager) agent.getCore().getPrimaryInjector().getInstance(OutputManager.class);
    }

    private void verifyLoadedSinksForConfig(
        final String expectationString,
        final String configName,
        final List<List<String>> expectedSinks
    ) {
        final Path configPath = resource(configName);
        final CoreOutputManager outputManager = getOutputManager(configPath);
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

    private Path resource(String name) {
        final String path = Objects.requireNonNull(getClass().getClassLoader().getResource(name)).getPath();
        return Paths.get(path);
    }
}
