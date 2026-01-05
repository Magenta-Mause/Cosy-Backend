package com.magentamause.cosybackend.exceptions;

public class ServerAlreadyStoppedException extends RuntimeException {
    public ServerAlreadyStoppedException(String serverName) {
        super("Server '" + serverName + "' is already stopped");
    }
}
