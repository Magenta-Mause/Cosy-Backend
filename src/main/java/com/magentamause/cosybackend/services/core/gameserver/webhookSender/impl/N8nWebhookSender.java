package com.magentamause.cosybackend.services.core.gameserver.webhookSender.impl;

import com.magentamause.cosybackend.entities.WebhookType;
import com.magentamause.cosybackend.services.core.gameserver.webhookSender.GameServerDomainEvent;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class N8nWebhookSender extends BaseWebhookSender {

    public N8nWebhookSender(@Qualifier("webhookWebClient") WebClient webClient) {
        super(webClient, WebhookType.N8N);
    }

    @Override
    public boolean supports(WebhookType type) {
        return type == WebhookType.N8N;
    }

    @Override
    protected Map<String, Object> payload(GameServerDomainEvent event) {
        return Map.of(
                "eventType", event.eventType().name(),
                "serverId", event.serverId(),
                "serverName", event.serverName(),
                "message", toMessage(event));
    }
}
