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

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.spotify.ffwd.Utils;
import java.time.Duration;
import org.junit.Test;

public class ExpiringCacheTest {

  @Test
  public void testIsCached() {
    final Cache<String, Boolean> cache =
        CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).build();
    final WriteCache expiringCache = new ExpiringCache(cache);
    assertFalse(expiringCache.checkCacheOrSet(Utils.makeMetric()));
    assertTrue(expiringCache.checkCacheOrSet(Utils.makeMetric()));
  }
}
