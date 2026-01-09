package com.magentamause.cosybackend.exceptions;

public class CreateGameInstanceException extends RuntimeException {
    public CreateGameInstanceException(String message) {
        super(message);
    }

    public CreateGameInstanceException(String message, Throwable cause) {
        super(message, cause);
    }

    public CreateGameInstanceException(Throwable cause) {
        super(cause);
    }
}
