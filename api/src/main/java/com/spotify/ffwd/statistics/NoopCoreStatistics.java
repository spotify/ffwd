/**
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
 **/
package com.spotify.ffwd.statistics;

public class NoopCoreStatistics implements CoreStatistics {
    private static final InputManagerStatistics noopInputManagerStatistics =
        new InputManagerStatistics() {
            @Override
            public void reportReceivedMetrics(int received) {
            }

            @Override
            public void reportReceivedEvents(int received) {
            }

            @Override
            public void reportEventsDroppedByFilter(int dropped) {
            }

            @Override
            public void reportMetricsDroppedByFilter(int dropped) {
            }
        };

    @Override
    public InputManagerStatistics newInputManager() {
        return noopInputManagerStatistics;
    }

    private static final OutputManagerStatistics noopOutputManagerStatistics =
        new OutputManagerStatistics() {
            @Override
            public void reportSentMetrics(int sent) {
            }

            @Override
            public void reportSentEvents(int sent) {
            }

            @Override
            public void reportEventsDroppedByFilter(int dropped) {
            }

            @Override
            public void reportMetricsDroppedByFilter(int dropped) {
            }
        };

    @Override
    public OutputManagerStatistics newOutputManager() {
        return noopOutputManagerStatistics;
    }

    private static final OutputPluginStatistics noopOutputPluginStatistics =
        new OutputPluginStatistics() {
            @Override
            public void reportDropped(int dropped) {
            }
        };

    @Override
    public OutputPluginStatistics newOutputPlugin(String id) {
        return noopOutputPluginStatistics;
    }

    private static final NoopCoreStatistics instance = new NoopCoreStatistics();

    public static NoopCoreStatistics get() {
        return instance;
    }
}
