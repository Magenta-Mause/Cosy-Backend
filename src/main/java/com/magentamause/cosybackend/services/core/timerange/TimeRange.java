package com.magentamause.cosybackend.services.core.timerange;

import java.time.Instant;

/** A resolved, validated half-open interval {@code [start, end)}. */
public record TimeRange(Instant start, Instant end) {

    public boolean isEmpty() {
        return !start.isBefore(end);
    }
}
