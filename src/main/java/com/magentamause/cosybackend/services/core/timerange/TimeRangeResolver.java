package com.magentamause.cosybackend.services.core.timerange;

import com.magentamause.cosybackend.configs.properties.TimeRangeProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Resolves and validates requested time ranges against {@link TimeRangeProperties}. */
public class TimeRangeResolver {

    private final TimeRangeProperties properties;
    private final Clock clock;

    public TimeRangeResolver(TimeRangeProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @param start requested start, {@code null} for {@code end - defaultSpan}
     * @param end requested end, {@code null} for now. An end in the future is clamped to now so
     *     that a client clock running ahead or a date picker selecting "end of today" still works.
     * @param restricted whether the caller only has public access and is limited to {@code
     *     publicMaxLookback}
     */
    public TimeRange resolve(Instant start, Instant end, boolean restricted) {
        Instant now = clock.instant();
        Instant resolvedEnd = end == null || end.isAfter(now) ? now : end;
        Instant resolvedStart = start != null ? start : resolvedEnd.minus(properties.defaultSpan());

        if (!resolvedStart.isBefore(resolvedEnd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start must be before end");
        }
        if (Duration.between(resolvedStart, resolvedEnd).compareTo(properties.maxSpan()) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "time range must not exceed " + properties.maxSpan().toDays() + " days");
        }
        if (restricted && resolvedStart.isBefore(now.minus(properties.publicMaxLookback()))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "public access is limited to the last "
                            + properties.publicMaxLookback().toHours()
                            + " hours");
        }

        return new TimeRange(resolvedStart, resolvedEnd);
    }

    /**
     * Cuts off the part of the range that lies beyond log retention — Loki holds no data there. The
     * result is empty if the whole range is older than the retention period.
     */
    public TimeRange clampToLogRetention(TimeRange range) {
        Instant retentionStart = clock.instant().minus(properties.logRetention());
        if (!range.start().isBefore(retentionStart)) {
            return range;
        }
        Instant end = range.end().isBefore(retentionStart) ? retentionStart : range.end();
        return new TimeRange(retentionStart, end);
    }
}
