package com.magentamause.cosybackend.services.core.logs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LogTimestampSequencerTest {

    private static final Instant T = Instant.parse("2026-09-27T12:00:00.123Z");

    private final LogTimestampSequencer sequencer = new LogTimestampSequencer();

    @Test
    void keepsDistinctIncreasingTimestampsUnchanged() {
        assertThat(sequencer.next(T)).isEqualTo(T);
        assertThat(sequencer.next(T.plusMillis(1))).isEqualTo(T.plusMillis(1));
    }

    @Test
    void separatesLinesWithinTheSameMillisecond() {
        Instant first = sequencer.next(T);
        Instant second = sequencer.next(T);
        Instant third = sequencer.next(T);

        assertThat(first).isEqualTo(T);
        assertThat(second).isEqualTo(T.plusNanos(1));
        assertThat(third).isEqualTo(T.plusNanos(2));
    }

    @Test
    void neverGoesBackwardsWhenTheClockDoes() {
        Instant first = sequencer.next(T);
        Instant earlier = sequencer.next(T.minusMillis(5));

        assertThat(earlier).isAfter(first);
    }

    @Test
    void convertsToEpochNanosLosslessly() {
        Instant precise = Instant.parse("2026-09-27T12:00:00.123456789Z");

        long nanos = LogTimestampSequencer.toEpochNanos(precise);

        assertThat(Instant.ofEpochSecond(0, nanos)).isEqualTo(precise);
    }
}
