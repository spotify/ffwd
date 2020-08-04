/*-
 * -\-\-
 * FastForward OpenCensus Module
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


package com.spotify.ffwd.opencensus;

import com.google.inject.Inject;
import com.spotify.ffwd.model.Batch;
import com.spotify.ffwd.model.Metric;
import com.spotify.ffwd.output.PluginSink;
import com.spotify.ffwd.util.BatchMetricConverter;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.AsyncFuture;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsConfiguration;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.stats.Aggregation.LastValue;
import io.opencensus.stats.Measure.MeasureDouble;
import io.opencensus.stats.Stats;
import io.opencensus.stats.StatsRecorder;
import io.opencensus.stats.View.Name;
import io.opencensus.stats.View;
import io.opencensus.tags.TagContext;
import io.opencensus.tags.TagContextBuilder;
import io.opencensus.tags.TagKey;
import io.opencensus.tags.TagValue;
import io.opencensus.tags.Tagger;
import io.opencensus.tags.Tags;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.text.StringSubstitutor;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/***
 * PluginSink to export metrics into Stackdriver via OpenCensus API.
 *
 * Right now, this is specifically designed to export event counts for
 * use in SLAs but could easily be extended to support more view types
 * by allowing them to be specified in config.
 *
 * Resources can be specified using the environment variables
 * OC_RESOURCE_TYPE and OC_RESOURCE_LABELS until the api becomes stable
 * https://www.javadoc.io/doc/io.opencensus/opencensus-api/latest/io/opencensus/resource/
package-summary.html
 */
public class OpenCensusPluginSink implements PluginSink  {
  private static final Logger log = LoggerFactory.getLogger(OpenCensusPluginSink.class);
  @Inject
  private AsyncFramework async;

  private Map<String, MeasureDouble> measures;

  private static final Tagger tagger = Tags.getTagger();
  private static final StatsRecorder statsRecorder = Stats.getStatsRecorder();

  private Optional<String> gcpProject;
  private int maxViews;
  private String outputMetricNamePattern;

  public void init() {
    // Is this actually needed, not sure. Better safe than sorry!
    measures = new ConcurrentHashMap<String, MeasureDouble>();
  }

  public OpenCensusPluginSink(Optional<String> gcpProject, Optional<Integer> maxViews,
      Optional<String> outputMetricNamePattern) {
    this.gcpProject = gcpProject;
    this.maxViews = maxViews.orElse(500);
    this.outputMetricNamePattern = outputMetricNamePattern.orElse("${key}_${what}");
  }

  public void sendMetric(Metric metric) {
    try {
      String metricName = getOutputMetricName(metric);
      MeasureDouble measure = measures.get(metricName);

      if (measure == null) {
        if (measures.size() > maxViews) {
          throw new RuntimeException("maxViews exceeded. " +
              "Please increase in configuration or decrease number of metrics.");
        }

        measure = MeasureDouble.create("Metric", "Value", "1");
        measures.put(metricName, measure);

        // Stackdriver expects each metric to have the same set of tags so metrics
        // missing tags will be rejected. NB by default stackdriver will create
        // the metricDescription based on the first metric received so be consistant
        // from the start.
        final List<TagKey> columns = new ArrayList<TagKey>(metric.getTags().size());
        metric.getTags().keySet().forEach(tagName -> {
            columns.add(TagKey.create(sanitizeName(tagName)));
        });
        final View view =
            View.create(
                Name.create(metricName),
                metricName,
                measure,
                LastValue.create(),
                columns);

        Stats.getViewManager().registerView(view);
      }
      final TagContextBuilder builder = tagger.emptyBuilder();
      metric.getTags().forEach((k, v) -> {
          builder.putPropagating(TagKey.create(sanitizeName(k)), TagValue.create(v));
      });
      final TagContext context = builder.build();

      statsRecorder.newMeasureMap().put(measure, metric.getValue()).record(context);
    } catch (Exception ex) {
      log.error("Couldn't send metric %s", ex);
      throw ex;
    }
  }

  public void sendBatch(Batch batch) {
    // NB: Unfortunately batches and measureMaps are not the same.
    batch.getPoints().forEach(point -> {
      sendMetric(BatchMetricConverter.convertBatchMetric(batch, point));
    });
  }

  public AsyncFuture<Void> start() {
    // NB using Application Default Credentials here:
    // See https://developers.google.com/identity/protocols/application-default-credentials
    try {
      StackdriverStatsConfiguration.Builder builder = StackdriverStatsConfiguration.builder();

      // We clear out the constant labels because otherwise they are populated with
      // values from this VM which is unlikely to be what we want.
      builder.setConstantLabels(Collections.emptyMap());

      // This can also be done by setting enviroment variables but it'll be frequently
      // be used so let's make it easy.
      if (gcpProject.isPresent()) {
        builder.setProjectId(gcpProject.get());
      }

      StackdriverStatsExporter.createAndRegister(builder.build());
    } catch (IOException ex) {
      log.error("Couldn't connect to Stackdriver");
      return async.failed(ex);
    }
    return async.resolved();
  }

  public AsyncFuture<Void> stop() {
    return async.resolved();
  }

  public boolean isReady() {
    return true;
  }

  private String getOutputMetricName(Metric metric) {
    StringSubstitutor sub = new StringSubstitutor((key) -> {
      if (key.equals("key")) {
        return metric.getKey();
      } else {
        return metric.getTags().get(key);
      }
    });
    return sanitizeName(sub.replace(outputMetricNamePattern));
  }

  private String sanitizeName(String name) {
    // acording to https://cloud.google.com/monitoring/api/ref_v3/rest/v3/projects.metricDescriptors
    // metric types and labels should only contain alphanumerics and underscores and shouldn't start
    // with an underscore.
    String sanitizedName = name.replaceAll("[^A-Za-z0-9_]", "_");
    if (sanitizedName.startsWith("_")) {
      if (sanitizedName.length() > 1) {
        sanitizedName = sanitizedName.substring(1);
      } else {
        throw new RuntimeException("Invalid name '_'.");
      }
    }
    return sanitizedName;
  }

}
