package com.magentamause.cosybackend.configs.properties;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TimeRangePropertiesTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void defaultsAreValid() {
        assertThat(
                        validate(
                                Duration.ofHours(5),
                                Duration.ofDays(30),
                                Duration.ofHours(24),
                                Duration.ofDays(7)))
                .isEmpty();
    }

    @Test
    void zeroOrNegativeDurationsAreRejected() {
        assertThat(
                        validate(
                                Duration.ofHours(5),
                                Duration.ofDays(30),
                                Duration.ofHours(24),
                                Duration.ZERO))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("logRetention");
        assertThat(
                        validate(
                                Duration.ofHours(-1),
                                Duration.ofDays(30),
                                Duration.ofHours(24),
                                Duration.ofDays(7)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("defaultSpan");
    }

    @Test
    void windowsLargerThanTheMaximumSpanAreRejected() {
        assertThat(
                        validate(
                                Duration.ofDays(31),
                                Duration.ofDays(30),
                                Duration.ofHours(24),
                                Duration.ofDays(7)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("consistent");
        assertThat(
                        validate(
                                Duration.ofHours(5),
                                Duration.ofDays(1),
                                Duration.ofDays(2),
                                Duration.ofDays(7)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("consistent");
    }

    private static Set<ConstraintViolation<TimeRangeProperties>> validate(
            Duration defaultSpan,
            Duration maxSpan,
            Duration publicMaxLookback,
            Duration logRetention) {
        return validator.validate(
                new TimeRangeProperties(defaultSpan, maxSpan, publicMaxLookback, logRetention));
    }
}
