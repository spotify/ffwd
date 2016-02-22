package com.spotify.ffwd.filter;

import com.spotify.ffwd.model.Event;
import com.spotify.ffwd.model.Metric;

public interface Filter {
    public boolean matchesEvent(Event event);

    public boolean matchesMetric(Metric metric);
}