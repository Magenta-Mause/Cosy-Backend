package com.magentamause.cosybackend.exceptions;

public class GamesApiError extends RuntimeException {

    public GamesApiError(String message) {
        super(message);
    }

    public GamesApiError(String message, Throwable cause) {
        super(message, cause);
    }

    public GamesApiError(Throwable cause) {
        super(cause);
    }
}
