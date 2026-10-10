package com.magentamause.cosybackend.services.core.gameserver.webhookSender;

/**
 * Human-readable one-liner for a domain event, shared by the built-in senders and by the {@code
 * {{message}}} placeholder of custom webhooks so both describe an event the same way.
 */
public final class WebhookMessages {

    private WebhookMessages() {}

    public static String forEvent(GameServerDomainEvent event) {
        return switch (event.eventType()) {
            case SERVER_STARTED -> "Server started: " + event.serverName();
            case SERVER_STOPPED -> "Server stopped: " + event.serverName();
            case SERVER_FAILED -> "Server crashed: " + event.serverName();
        };
    }
}
