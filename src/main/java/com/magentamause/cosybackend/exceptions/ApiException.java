package com.magentamause.cosybackend.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A {@link ResponseStatusException} that also tells the client which failure it is. */
@Getter
public class ApiException extends ResponseStatusException {
    private final ErrorCode errorCode;

    public ApiException(HttpStatus status, ErrorCode errorCode, String reason) {
        super(status, reason);
        this.errorCode = errorCode;
    }
}
