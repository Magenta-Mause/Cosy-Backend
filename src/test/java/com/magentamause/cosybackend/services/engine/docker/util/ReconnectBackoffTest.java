package com.magentamause.cosybackend.services.engine.docker.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ReconnectBackoffTest {

    private static final Duration INITIAL_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_DELAY = Duration.ofSeconds(30);

    @Test
    void firstAttemptWaitsAboutTheInitialDelay() {
        ReconnectBackoff backoff = new ReconnectBackoff(INITIAL_DELAY, MAX_DELAY, 0.2);

        assertThat(backoff.delayForAttempt(0))
                .isBetween(Duration.ofMillis(800), Duration.ofMillis(1000));
    }

    @Test
    void delayGrowsExponentiallyAndIsCappedAtTheMaximum() {
        ReconnectBackoff backoff = new ReconnectBackoff(INITIAL_DELAY, MAX_DELAY, 0);

        assertThat(backoff.delayForAttempt(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(backoff.delayForAttempt(4)).isEqualTo(Duration.ofSeconds(16));
        assertThat(backoff.delayForAttempt(5)).isEqualTo(MAX_DELAY);
        // A long-running outage must not overflow into a negative or absurd delay.
        assertThat(backoff.delayForAttempt(Integer.MAX_VALUE)).isEqualTo(MAX_DELAY);
    }

    @Test
    void jitterKeepsTheDelayWithinTheConfiguredBand() {
        ReconnectBackoff backoff = new ReconnectBackoff(INITIAL_DELAY, MAX_DELAY, 0.5);

        for (int i = 0; i < 100; i++) {
            assertThat(backoff.delayForAttempt(3))
                    .isBetween(Duration.ofSeconds(4), Duration.ofSeconds(8));
        }
    }

    @Test
    void rejectsAnInvalidConfiguration() {
        assertThatThrownBy(() -> new ReconnectBackoff(MAX_DELAY, INITIAL_DELAY, 0.2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReconnectBackoff(INITIAL_DELAY, MAX_DELAY, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
