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
            ImmutableMap.of("FFWD_TAG_foo", "bar", "PATH", "ignored:ignored", "FFWD_TAG_bar",
                "baz"));

        assertEquals(ImmutableMap.of("foo", "bar", "bar", "baz"), out);
    }
}
