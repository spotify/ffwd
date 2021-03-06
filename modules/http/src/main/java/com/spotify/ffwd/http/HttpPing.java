/*-
 * -\-\-
 * FastForward HTTP Module
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

package com.spotify.ffwd.http;

import com.netflix.loadbalancer.IPing;
import com.netflix.loadbalancer.Server;
import javax.inject.Inject;
import org.slf4j.Logger;

public class HttpPing implements IPing {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(HttpPing.class);

  @Inject
  HttpClientFactory clientFactory;

  @Override
  public boolean isAlive(final Server server) {
    try {
      clientFactory.newClient(server).ping().get();
    } catch (Exception e) {
      log.warn("Error when pinging server ({}): {}", server, e.getMessage());
      log.trace("Error when pinging server ({}): {}", server, e.getMessage(), e);
      return false;
    }

    return true;
  }
}
