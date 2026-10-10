package com.magentamause.cosybackend.configs.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limits for the time ranges requested by the log and metric endpoints.
 *
 * @param defaultSpan span used when a request omits {@code start}
 * @param maxSpan longest span a single request may cover
 * @param publicMaxLookback how far back callers without the read permission (public dashboard
 *     visitors) may query
 * @param logRetention how long Loki keeps logs; log queries are clamped to it. Keep in sync with
 *     Loki's {@code retention_period}.
 */
@Validated
@ConfigurationProperties("cosy.time-range")
public record TimeRangeProperties(
        @NotNull @DurationMin(nanos = 1) Duration defaultSpan,
        @NotNull @DurationMin(nanos = 1) Duration maxSpan,
        @NotNull @DurationMin(nanos = 1) Duration publicMaxLookback,
        @NotNull @DurationMin(nanos = 1) Duration logRetention) {

    /** The default and the public window must each fit into a single allowed range. */
    @AssertTrue(message = "default-span and public-max-lookback must not exceed max-span")
    public boolean isConsistent() {
        return defaultSpan == null
                || maxSpan == null
                || publicMaxLookback == null
                || (defaultSpan.compareTo(maxSpan) <= 0
                        && publicMaxLookback.compareTo(maxSpan) <= 0);
    }
}
