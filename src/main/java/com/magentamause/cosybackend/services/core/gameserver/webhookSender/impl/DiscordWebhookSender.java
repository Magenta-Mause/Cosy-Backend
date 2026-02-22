package com.magentamause.cosybackend.services.core.gameserver.webhookSender.impl;

import com.magentamause.cosybackend.entities.WebhookType;
import com.magentamause.cosybackend.services.core.gameserver.webhookSender.GameServerDomainEvent;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class DiscordWebhookSender extends BaseWebhookSender {

    public DiscordWebhookSender(@Qualifier("webhookWebClient") WebClient webClient) {
        super(webClient, WebhookType.DISCORD);
    }

    @Override
    public boolean supports(WebhookType type) {
        return type == WebhookType.DISCORD;
    }

    @Override
    protected Map<String, Object> payload(GameServerDomainEvent event) {
        String statusEmoji;
        int color;

        switch (event.eventType()) {
            case SERVER_STARTED -> {
                statusEmoji = "✅";
                color = 0x57F287; // green
            }
            case SERVER_STOPPED -> {
                statusEmoji = "⏹️";
                color = 0x99AAB5; // gray
            }
            case SERVER_FAILED -> {
                statusEmoji = "❌";
                color = 0xED4245; // red
            }
            default -> {
                statusEmoji = "ℹ️";
                color = 0x5865F2; // blurple
            }
        }

        Map<String, Object> fieldServer =
                Map.of("name", "Server", "value", event.serverName(), "inline", true);

        Map<String, Object> fieldId =
                Map.of("name", "Server ID", "value", event.serverId(), "inline", true);

        Map<String, Object> fieldEvent =
                Map.of("name", "Event", "value", event.eventType().name(), "inline", false);

        Map<String, Object> footer = Map.of("text", "Cosy Game Server");

        Map<String, Object> embed =
                Map.of(
                        "title",
                        statusEmoji + " " + toMessage(event),
                        "description",
                        "Status update for your game server.",
                        "color",
                        color,
                        "fields",
                        java.util.List.of(fieldServer, fieldId, fieldEvent),
                        "footer",
                        footer,
                        "timestamp",
                        java.time.OffsetDateTime.now().toString());

        return Map.of(
                // Optional plain text line above the embed
                "content", "", "embeds", java.util.List.of(embed));
    }
}
