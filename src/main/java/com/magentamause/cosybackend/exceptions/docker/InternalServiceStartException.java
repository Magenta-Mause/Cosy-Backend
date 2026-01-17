package com.magentamause.cosybackend.exceptions.docker;

import com.github.dockerjava.api.exception.InternalServerErrorException;
import lombok.Getter;

public class InternalServiceStartException extends Exception {
    @Getter private final Exception originalException;

    public InternalServiceStartException(Exception originalException) {
        super(originalException.getMessage(), originalException);
        this.originalException = originalException;
    }
}
