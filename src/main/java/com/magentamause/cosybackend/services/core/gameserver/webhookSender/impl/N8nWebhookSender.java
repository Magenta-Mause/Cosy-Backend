package com.magentamause.cosybackend.services.core.gameserver.webhookSender.impl;

import com.magentamause.cosybackend.entities.WebhookEntity;
import com.magentamause.cosybackend.entities.WebhookType;
import com.magentamause.cosybackend.services.core.gameserver.webhookSender.GameServerDomainEvent;
import com.magentamause.cosybackend.services.core.gameserver.webhookSender.WebhookSender;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class N8nWebhookSender implements WebhookSender {

    private final WebClient webClient;

    public N8nWebhookSender(@Qualifier("gamesApiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public boolean supports(WebhookType type) {
        return type == WebhookType.N8N;
    }

    @Override
    public void send(WebhookEntity webhook, GameServerDomainEvent event) {
        try {
            webClient
                    .mutate()
                    .build()
                    .post()
                    .uri(webhook.getWebhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(
                            Map.of(
                                    "eventType", event.eventType().name(),
                                    "serverId", event.serverId(),
                                    "serverName", event.serverName(),
                                    "message", toMessage(event)))
                    .retrieve()
                    .toBodilessEntity()
                    .doOnError(
                            ex ->
                                    log.error(
                                            "Failed to send n8n webhook for server {} and event {}",
                                            event.serverId(),
                                            event.eventType(),
                                            ex))
                    .subscribe();
        } catch (Exception ex) {
            log.error(
                    "Failed to prepare n8n webhook request for server {} and event {}",
                    event.serverId(),
                    event.eventType(),
                    ex);
        }
    }

    private String toMessage(GameServerDomainEvent event) {
        return switch (event.eventType()) {
            case SERVER_STARTED -> "✅ Server started: " + event.serverName();
            case SERVER_STOPPED -> "🛑 Server stopped: " + event.serverName();
            case SERVER_FAILED -> "❌ Server crashed: " + event.serverName();
        };
    }
}
