package com.spotify.ffwd.http;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import rx.Observable;
import rx.functions.Func1;

public class RetryWithDelay implements Func1<Observable<? extends Throwable>, Observable<?>> {
    private final int maxRetries;
    private long retryDelayMillis;
    private final long maxDelayMillis;
    private int retryCount;
    private final Random random = new Random();

    public RetryWithDelay(final int maxRetries, final long retryDelayMillis, final long maxDelayMillis) {
        this.maxRetries = maxRetries;
        this.retryDelayMillis = retryDelayMillis;
        this.maxDelayMillis = maxDelayMillis;
        this.retryCount = 0;
    }

    @Override
    public Observable<?> call(Observable<? extends Throwable> attempts) {
        return attempts.flatMap(new Func1<Throwable, Observable<?>>() {
            @Override
            public Observable<?> call(Throwable throwable) {
                if (++retryCount < maxRetries) {
                    final long retryMillis = retryDelayMillis;
                    final long jitter = (long)(random.nextFloat() * retryMillis);

                    retryDelayMillis = Math.min(retryDelayMillis * 2, maxDelayMillis);
                    return Observable.timer(retryMillis + jitter, TimeUnit.MILLISECONDS);
                }

                // Max retries hit. Just pass the error along.
                return Observable.error(throwable);
            }
        });
    }
}