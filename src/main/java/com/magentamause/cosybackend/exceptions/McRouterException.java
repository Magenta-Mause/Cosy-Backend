package com.magentamause.cosybackend.exceptions;

public class McRouterException extends Exception {
    public McRouterException(String message) {
        super(message);
    }

    public McRouterException(String message, Throwable cause) {
        super(message, cause);
    }
}
