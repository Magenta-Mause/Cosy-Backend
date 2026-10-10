package com.magentamause.cosybackend.services.core.logs;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Gives every stored log line a unique, strictly increasing nanosecond timestamp.
 *
 * <p>Log pagination uses the oldest returned timestamp as the exclusive end of the next page, so
 * two lines sharing a timestamp across a page boundary would make the second one unreachable. Game
 * servers regularly print bursts of lines within the same millisecond; nudging a colliding
 * timestamp by a nanosecond keeps the order and makes the cursor exact.
 */
@Component
public class LogTimestampSequencer {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final AtomicLong lastNanos = new AtomicLong();

    public Instant next(Instant candidate) {
        long candidateNanos = toEpochNanos(candidate);
        long nanos = lastNanos.accumulateAndGet(candidateNanos, (last, c) -> Math.max(last + 1, c));
        return Instant.ofEpochSecond(nanos / NANOS_PER_SECOND, nanos % NANOS_PER_SECOND);
    }

    public static long toEpochNanos(Instant instant) {
        return Math.addExact(
                Math.multiplyExact(instant.getEpochSecond(), NANOS_PER_SECOND), instant.getNano());
    }
}
