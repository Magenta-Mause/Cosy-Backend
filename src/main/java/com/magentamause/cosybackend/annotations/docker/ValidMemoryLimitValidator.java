package com.magentamause.cosybackend.annotations.docker;

import com.magentamause.cosybackend.services.engine.docker.util.MemoryUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ValidMemoryLimitValidator implements ConstraintValidator<ValidMemoryLimit, String> {

    private long minBytes;

    @Override
    public void initialize(ValidMemoryLimit constraintAnnotation) {
        this.minBytes = constraintAnnotation.minBytes();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // nullable
        }

        try {
            long bytes = MemoryUtils.parseMemoryStringToBytes(value);
            log.info("Memory limit: {} bytes", bytes);
            return bytes >= minBytes;
        } catch (IllegalArgumentException ex) {
            return false; // invalid format
        }
    }
}
