/*
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
 */
package com.spotify.ffwd.kafka;

import kafka.producer.Partitioner;
import kafka.utils.VerifiableProperties;

public class IntegerPartitioner implements Partitioner {
    public IntegerPartitioner(VerifiableProperties properties) {
    }

    @Override
    public int partition(Object o, int partitions) {
        int i = Math.abs((int) o);
        return i % partitions;
    }
}
