package com.magentamause.cosybackend.services.core.timerange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.magentamause.cosybackend.configs.properties.TimeRangeProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class TimeRangeResolverTest {

    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");
    private static final TimeRangeProperties PROPERTIES =
            new TimeRangeProperties(
                    Duration.ofHours(5),
                    Duration.ofDays(30),
                    Duration.ofHours(24),
                    Duration.ofDays(7));

    private final TimeRangeResolver resolver =
            new TimeRangeResolver(PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void missingBoundsDefaultToTheLastDefaultSpan() {
        TimeRange range = resolver.resolve(null, null, false);

        assertThat(range.end()).isEqualTo(NOW);
        assertThat(range.start()).isEqualTo(NOW.minus(Duration.ofHours(5)));
    }

    @Test
    void missingStartIsDerivedFromTheGivenEnd() {
        Instant end = NOW.minus(Duration.ofDays(2));

        TimeRange range = resolver.resolve(null, end, false);

        assertThat(range.start()).isEqualTo(end.minus(Duration.ofHours(5)));
        assertThat(range.end()).isEqualTo(end);
    }

    @Test
    void endInTheFutureIsClampedToNow() {
        TimeRange range =
                resolver.resolve(
                        NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(8)), false);

        assertThat(range.end()).isEqualTo(NOW);
    }

    @Test
    void startNotBeforeEndIsRejected() {
        assertThatThrownBy(
                        () -> resolver.resolve(NOW.minusSeconds(10), NOW.minusSeconds(10), false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void startInTheFutureIsRejected() {
        assertThatThrownBy(() -> resolver.resolve(NOW.plusSeconds(60), null, false))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void spanOfExactlyTheMaximumIsAccepted() {
        TimeRange range = resolver.resolve(NOW.minus(Duration.ofDays(30)), NOW, false);

        assertThat(range.start()).isEqualTo(NOW.minus(Duration.ofDays(30)));
    }

    @Test
    void spanSlightlyBeyondTheMaximumIsTrimmed() {
        // A "last 30 days" preset computed on the client arrives a few ms over the limit.
        TimeRange range =
                resolver.resolve(NOW.minus(Duration.ofDays(30)).minusMillis(250), null, false);

        assertThat(range.start()).isEqualTo(NOW.minus(Duration.ofDays(30)));
        assertThat(range.end()).isEqualTo(NOW);
    }

    @Test
    void spanBeyondTheMaximumAndTheToleranceIsRejected() {
        assertThatThrownBy(
                        () ->
                                resolver.resolve(
                                        NOW.minus(Duration.ofDays(30))
                                                .minus(TimeRangeResolver.CLOCK_TOLERANCE)
                                                .minusSeconds(1),
                                        NOW,
                                        false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void restrictedCallerMayQueryUpToThePublicLookback() {
        TimeRange range = resolver.resolve(NOW.minus(Duration.ofHours(24)), null, true);

        assertThat(range.start()).isEqualTo(NOW.minus(Duration.ofHours(24)));
    }

    @Test
    void restrictedCallerSlightlyBeyondThePublicLookbackIsTrimmed() {
        TimeRange range =
                resolver.resolve(NOW.minus(Duration.ofHours(24)).minusSeconds(2), null, true);

        assertThat(range.start()).isEqualTo(NOW.minus(Duration.ofHours(24)));
    }

    @Test
    void restrictedCallerMayNotQueryBeyondThePublicLookback() {
        assertThatThrownBy(
                        () ->
                                resolver.resolve(
                                        NOW.minus(Duration.ofHours(24))
                                                .minus(TimeRangeResolver.CLOCK_TOLERANCE)
                                                .minusSeconds(1),
                                        null,
                                        true))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void restrictedLookbackIsMeasuredFromNowNotFromEnd() {
        Instant end = NOW.minus(Duration.ofHours(23));

        assertThatThrownBy(() -> resolver.resolve(end.minus(Duration.ofHours(2)), end, true))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rangeWithinLogRetentionIsUnchanged() {
        TimeRange range = new TimeRange(NOW.minus(Duration.ofDays(6)), NOW);

        assertThat(resolver.clampToLogRetention(range)).isEqualTo(range);
    }

    @Test
    void rangeReachingPastLogRetentionIsCutAtTheRetentionBoundary() {
        TimeRange range = new TimeRange(NOW.minus(Duration.ofDays(30)), NOW);

        TimeRange clamped = resolver.clampToLogRetention(range);

        assertThat(clamped.start()).isEqualTo(NOW.minus(Duration.ofDays(7)));
        assertThat(clamped.end()).isEqualTo(NOW);
        assertThat(clamped.isEmpty()).isFalse();
    }

    @Test
    void rangeEntirelyOlderThanLogRetentionBecomesEmpty() {
        TimeRange range =
                new TimeRange(NOW.minus(Duration.ofDays(20)), NOW.minus(Duration.ofDays(10)));

        assertThat(resolver.clampToLogRetention(range).isEmpty()).isTrue();
    }

    private static void assertStatus(Throwable e, HttpStatus status) {
        assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(status);
    }
}
