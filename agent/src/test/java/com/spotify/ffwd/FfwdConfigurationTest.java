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

import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FfwdConfigurationTest {

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
            "com.spotify.ffwd.output.BatchingPluginSink",
            "com.spotify.ffwd.signalfx.SignalFxPluginSink"));

    verifyLoadedSinksForConfig(
        "List of sink chains match the expectation, with batch and/or filter for some",
        "ffwd-mixed-plugins.yaml", expectedSinks);
  }

  @Test
  public void testConfigFromEnvVars() throws Exception {
    CoreOutputManager outputManager = withEnvironmentVariable("FFWD_TTL", "100")
        .and("FFWD_OUTPUT_RATELIMIT", "1000")
        .and("FFWD_OUTPUT_CARDINALITYLIMIT", "10000")
        .and("FFWD_OUTPUT_CARDINALITYTTL", "3000000")
        .execute(() -> getOutputManager(null));

    assertEquals(100, outputManager.getTtl());
    assertEquals(Long.valueOf(1000), outputManager.getRateLimit());
    assertEquals(Long.valueOf(10000), outputManager.getCardinalityLimit());
    assertEquals(Long.valueOf(3000000), outputManager.getHLLPRefreshPeriodLimit());
  }

  @Test
  public void testIgnoreUnknownFields() {
    Path configPath = resource("invalid.yaml");

    String host = getOutputManager(configPath).getHost();
    assertEquals("jimjam", host);
  }

  @Test
  public void testMergeOrder() throws Exception {
    Path configPath = resource("basic-settings.yaml");
    CoreOutputManager outputManager = withEnvironmentVariable("FFWD_TTL", "100")
        .execute(() -> getOutputManager(configPath));

    assertEquals(100, outputManager.getTtl());
    assertEquals("jimjam", outputManager.getHost());
    assertEquals(Long.valueOf(10000), outputManager.getCardinalityLimit());
    assertEquals(Long.valueOf(555555), outputManager.getHLLPRefreshPeriodLimit());
  }

  @Test
  public void testDynamicTagsFile() throws Exception {
    Path configPath = resource("basic-settings-with-dynamic-tags-file.yaml");
    CoreOutputManager outputManager = withEnvironmentVariable("FFWD_TAG_foo", "bar")
        .and("FFWD_TAG_other", "constant")
        .and("IS_FFWD_CONFIGURATION_TEST", "1")
        .execute(() -> getOutputManager(configPath));

    // At first tags should have the foo=bar entry set by the env var above.
    assertThat(outputManager.getTags(), hasEntry("foo", "bar"));
    // Then the tag value should be updated from bar to qux.
    await().atMost(5, SECONDS).until(
        () -> {
          final Map<String, String> tags = outputManager.getTags();
          return Objects.equals(tags.get("foo"), "qux")
                 && Objects.equals(tags.get("other"), "constant")
                 && tags.get("not-specified") == null;
        });
  }

  @Test
  public void testDynamicTagsFileDoesNotExist() throws Exception {
    Path configPath = resource("basic-settings-with-non-existent-dynamic-tags-file.yaml");
    CoreOutputManager outputManager = withEnvironmentVariable("FFWD_TAG_foo", "bar")
        .execute(() -> getOutputManager(configPath));
    assertThat(outputManager.getTags(), hasEntry("foo", "bar"));
  }

  private CoreOutputManager getOutputManager(final Path configPath) {
    final FastForwardAgent agent = FastForwardAgent.setup(Optional.ofNullable(configPath));
    return (CoreOutputManager) agent.getCore().getPrimaryInjector()
        .getInstance(OutputManager.class);
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
    final String path =
        Objects.requireNonNull(getClass().getClassLoader().getResource(name)).getPath();
    return Paths.get(path);
  }
}
