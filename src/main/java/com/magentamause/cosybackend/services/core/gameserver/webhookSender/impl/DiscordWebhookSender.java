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
        super(webClient);
    }

    @Override
    public boolean supports(WebhookType type) {
        return type == WebhookType.DISCORD;
    }

    @Override
    protected String webhookTypeName() {
        return "discord";
    }

    @Override
    protected Map<String, Object> payload(GameServerDomainEvent event) {
        return Map.of("content", toMessage(event));
    }
}
