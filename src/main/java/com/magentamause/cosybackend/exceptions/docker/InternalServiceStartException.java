package com.magentamause.cosybackend.exceptions.docker;

import lombok.Getter;

public class InternalServiceStartException extends Exception {
    @Getter private final Exception originalException;

    public InternalServiceStartException(Exception originalException) {
        super(originalException.getMessage(), originalException);
        this.originalException = originalException;
    }
}
