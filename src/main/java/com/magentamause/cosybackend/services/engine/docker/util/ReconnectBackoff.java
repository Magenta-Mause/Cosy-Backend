package com.magentamause.cosybackend.services.engine.docker.util;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Capped exponential backoff with jitter, used by the Docker stream subscriptions to retry a lost
 * connection without hammering the Docker daemon.
 *
 * <p>The jitter keeps independent streams (event subscription, per-container log attachments) from
 * reconnecting in lockstep after a shared outage.
 */
public final class ReconnectBackoff {

    private static final Duration DEFAULT_INITIAL_DELAY = Duration.ofSeconds(1);
    private static final Duration DEFAULT_MAX_DELAY = Duration.ofSeconds(30);
    private static final double DEFAULT_JITTER_RATIO = 0.2;

    /** Guards against overflow when shifting; 2^30 x the initial delay is far beyond maxDelay. */
    private static final int MAX_SHIFT = 30;

    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double jitterRatio;

    public ReconnectBackoff(Duration initialDelay, Duration maxDelay, double jitterRatio) {
        if (initialDelay.isNegative() || maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException(
                    "maxDelay must be >= initialDelay and both must be non-negative");
        }
        if (jitterRatio < 0 || jitterRatio > 1) {
            throw new IllegalArgumentException("jitterRatio must be within [0, 1]");
        }
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.jitterRatio = jitterRatio;
    }

    public static ReconnectBackoff defaultBackoff() {
        return new ReconnectBackoff(DEFAULT_INITIAL_DELAY, DEFAULT_MAX_DELAY, DEFAULT_JITTER_RATIO);
    }

    /**
     * Delay before retry number {@code attempt}, where {@code attempt} is 0 for the first retry
     * after a working connection was lost.
     *
     * @return a duration within {@code [base * (1 - jitterRatio), base]}, never longer than the
     *     configured maximum delay
     */
    public Duration delayForAttempt(int attempt) {
        int shift = Math.min(Math.max(attempt, 0), MAX_SHIFT);
        long baseMillis = initialDelay.toMillis() << shift;
        long cappedMillis = Math.min(baseMillis, maxDelay.toMillis());
        if (cappedMillis <= 0 || jitterRatio == 0) {
            return Duration.ofMillis(cappedMillis);
        }
        long jitterMillis = (long) (cappedMillis * jitterRatio);
        long lowerBound = cappedMillis - jitterMillis;
        return Duration.ofMillis(
                ThreadLocalRandom.current().nextLong(lowerBound, cappedMillis + 1));
    }
}
