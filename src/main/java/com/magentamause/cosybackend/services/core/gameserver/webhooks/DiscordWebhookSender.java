package com.magentamause.cosybackend.services.core.gameserver.webhooks;

import com.magentamause.cosybackend.entities.GameServerEventType;
import com.magentamause.cosybackend.entities.GameServerWebhookEntity;
import com.magentamause.cosybackend.entities.WebhookType;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class DiscordWebhookSender implements WebhookSender {

    private final WebClient webClient;

    public DiscordWebhookSender(@Qualifier("gamesApiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public boolean supports(WebhookType type) {
        return type == WebhookType.DISCORD;
    }

    @Override
    public void send(GameServerWebhookEntity webhook, GameServerDomainEvent event) {
        try {
            webClient
                    .mutate()
                    .build()
                    .post()
                    .uri(webhook.getWebhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("content", toMessage(event.eventType())))
                    .retrieve()
                    .toBodilessEntity()
                    .doOnError(
                            ex ->
                                    log.error(
                                            "Failed to send discord webhook for server {} and event {}",
                                            event.serverId(),
                                            event.eventType(),
                                            ex))
                    .subscribe();
        } catch (Exception ex) {
            log.error(
                    "Failed to prepare discord webhook request for server {} and event {}",
                    event.serverId(),
                    event.eventType(),
                    ex);
        }
    }

    private String toMessage(GameServerEventType eventType) {
        return switch (eventType) {
            case SERVER_STARTED -> "✅ Server started";
            case SERVER_STOPPED -> "🛑 Server stopped";
            case SERVER_FAILED -> "❌ Server crashed";
        };
    }
}
