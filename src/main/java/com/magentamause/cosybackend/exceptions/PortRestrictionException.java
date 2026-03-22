package com.magentamause.cosybackend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class PortRestrictionException extends RuntimeException {
    public PortRestrictionException(String message) {
        super(message);
    }
}
