/*-
 * -\-\-
 * FastForward Core
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

package com.spotify.ffwd.output;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.Test;

public class OutputManagerModuleTest {
    /**
     * Test that a map containing environment variables correctly extracts and discards variables
     * changing the tags in ffwd.
     */
    @Test
    public void testFilterEnvironment() {
        final Map<String, String> out = OutputManagerModule.filterEnvironmentTags(
            ImmutableMap.of("FFWD_TAG_FOO", "bar", "PATH", "ignored:ignored", "FFWD_TAG_bar",
                "baz"));

        assertEquals(ImmutableMap.of("foo", "bar", "bar", "baz"), out);
    }
}
