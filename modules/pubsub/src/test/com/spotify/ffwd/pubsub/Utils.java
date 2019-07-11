package com.spotify.ffwd.pubsub;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.spotify.ffwd.model.Metric;
import java.util.Date;

public class Utils {

  public static Metric makeMetric() {
    return new Metric("key1", 1278, new Date(), ImmutableSet.of(),
      ImmutableMap.of("what", "test"), ImmutableMap.of(), null);
  }
}
