/*-
 * -\-\-
 * FastForward API
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


package com.spotify.ffwd.cache;

import com.google.common.cache.Cache;
import com.spotify.ffwd.model.Metric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ExpiringCache is useful to reduce the number of metrics being published to a topic. Typically
 * processing and de-deduplication of the "metadata" around a metric only needs to happen every so
 * often. In the case of using Heroic, this "metadata" only needs to be published at the interval of
 * a new index being created.
 */
public class ExpiringCache implements WriteCache {
  private static final Logger log = LoggerFactory.getLogger(ExpiringCache.class);
  private final Cache<String, Boolean> writeCache;

  public ExpiringCache(final Cache<String, Boolean> writeCache) {
    this.writeCache = writeCache;
  }

  @Override
  public boolean checkCacheOrSet(final Metric metric) {
    if (writeCache.getIfPresent(metric.generateHash()) != null) {
      log.trace("Metric in cache: {}", metric);
      return true;
    }
    writeCache.put(metric.generateHash(), true);
    return false;
  }

}
