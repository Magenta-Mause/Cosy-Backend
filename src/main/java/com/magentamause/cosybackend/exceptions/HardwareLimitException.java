package com.magentamause.cosybackend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class HardwareLimitException extends RuntimeException {
    public HardwareLimitException(String message) {
        super(message);
    }
}
