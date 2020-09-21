/*-
 * -\-\-
 * FastForward API
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

package com.spotify.ffwd.serializer;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.spotify.ffwd.cache.WriteCache;
import com.spotify.ffwd.model.Metric;
import java.util.Collection;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public interface Serializer {
    /**
     * Serialize {@link Metric}
     * @param metric
     * @return
     * @throws Exception
     */
    byte[] serialize(Metric metric) throws Exception;

    /**
     * Serialize a batch of {@link Metric}
     * @param metrics
     * @param writeCache
     * @return
     * @throws Exception
     */
    byte[] serialize(Collection<Metric> metrics, WriteCache writeCache) throws Exception;


    /**
     * Serialize {@link com.spotify.ffwd.model.v2.Metric}
     *
     * <p>This method doesn't have a default behavior it was added to maintain compatibility,
     * please provide an implementation that fit your use case
     *
     * @param metric
     * @return
     * @throws Exception
     */
    default byte[] serialize(com.spotify.ffwd.model.v2.Metric metric) throws Exception {
        throw new UnsupportedOperationException("Not supported");
    }


    /**
     * Serialize a batch of {@link com.spotify.ffwd.model.v2.Metric}
     *
     * <p>This method doesn't have a default behavior it was added to maintain compatibility,
     * please provide an implementation that fit your use case.
     *
     * @param metrics
     * @param writeCache
     * @return
     * @throws Exception
     */
    default byte[] serializeMetrics(final Collection<com.spotify.ffwd.model.v2.Metric> metrics,
                                    final WriteCache writeCache) throws Exception {
        throw new UnsupportedOperationException("Not supported");
        //TODO changer the name  or the param ?
    }
}
