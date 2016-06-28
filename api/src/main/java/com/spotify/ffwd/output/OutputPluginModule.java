package com.spotify.ffwd.output;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.PrivateModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.spotify.ffwd.statistics.CoreStatistics;
import com.spotify.ffwd.statistics.OutputPluginStatistics;

@RequiredArgsConstructor
public abstract class OutputPluginModule extends PrivateModule {
    private final String id;

    @Provides
    @Singleton
    public Logger logger() {
        final String logName = String.format("%s[output:%s]", getClass().getPackage().getName(), id);
        return LoggerFactory.getLogger(logName);
    }

    @Provides
    @Singleton
    public OutputPluginStatistics statistics(CoreStatistics statistics) {
        return statistics.newOutputPlugin(id);
    }
}