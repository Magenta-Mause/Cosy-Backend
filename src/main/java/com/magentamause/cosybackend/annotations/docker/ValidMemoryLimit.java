package com.magentamause.cosybackend.annotations.docker;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidMemoryLimitValidator.class)
public @interface ValidMemoryLimit {

    /** Default error message */
    String message() default "Memory must be at least 6MiB (e.g. 6MiB, 512MiB, 1GiB)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Minimum memory in bytes */
    long minBytes() default 6L * 1024 * 1024;
}
