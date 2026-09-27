package com.magentamause.cosybackend.configs.properties;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
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
        @NotNull Duration defaultSpan,
        @NotNull Duration maxSpan,
        @NotNull Duration publicMaxLookback,
        @NotNull Duration logRetention) {}
