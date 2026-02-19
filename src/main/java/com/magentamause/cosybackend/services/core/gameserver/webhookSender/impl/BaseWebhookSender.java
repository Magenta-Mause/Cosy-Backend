package com.magentamause.cosybackend.services.core.gameserver.webhookSender.impl;

import com.magentamause.cosybackend.entities.WebhookEntity;
import com.magentamause.cosybackend.entities.WebhookType;
import com.magentamause.cosybackend.services.core.gameserver.webhookSender.GameServerDomainEvent;
import com.magentamause.cosybackend.services.core.gameserver.webhookSender.WebhookSender;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@RequiredArgsConstructor
public abstract class BaseWebhookSender implements WebhookSender {

    private final WebClient webClient;
    private final WebhookType webhookType;

    @Override
    public void send(WebhookEntity webhook, GameServerDomainEvent event) {
        try {
            webClient
                    .post()
                    .uri(webhook.getWebhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload(event))
                    .retrieve()
                    .toBodilessEntity()
                    .doOnError(
                            ex ->
                                    log.error(
                                            "Failed to send {} webhook for server {} and event {}",
                                            webhookType.name().toLowerCase(),
                                            event.serverId(),
                                            event.eventType(),
                                            ex))
                    .subscribe();
        } catch (Exception ex) {
            log.error(
                    "Failed to prepare {} webhook request for server {} and event {}",
                    webhookType.name().toLowerCase(),
                    event.serverId(),
                    event.eventType(),
                    ex);
        }
    }

    protected String toMessage(GameServerDomainEvent event) {
        return switch (event.eventType()) {
            case SERVER_STARTED -> "Server started: " + event.serverName();
            case SERVER_STOPPED -> "Server stopped: " + event.serverName();
            case SERVER_FAILED -> "Server crashed: " + event.serverName();
        };
    }

    protected abstract Map<String, Object> payload(GameServerDomainEvent event);
}
