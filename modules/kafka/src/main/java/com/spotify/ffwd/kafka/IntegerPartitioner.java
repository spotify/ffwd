/*-
 * -\-\-
 * FastForward Kafka Module
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

package com.spotify.ffwd.kafka;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;

public class IntegerPartitioner implements Partitioner {
    public IntegerPartitioner() {
    }

    public IntegerPartitioner(Properties properties) {
    }

    @Override
    public int partition(
        final String topic, final Object o, final byte[] bytes, final Object o1,
        final byte[] bytes1, final Cluster cluster
    ) {
        List<PartitionInfo> partitions = cluster.partitionsForTopic(topic);
        int numPartitions = partitions.size();
        int i = Math.abs((int) o);
        return i % numPartitions;
    }

    @Override
    public void close() {
    }

    @Override
    public void configure(final Map<String, ?> map) {
    }
}
