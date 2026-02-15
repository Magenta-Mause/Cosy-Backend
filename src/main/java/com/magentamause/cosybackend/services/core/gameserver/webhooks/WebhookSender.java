package com.magentamause.cosybackend.services.core.gameserver.webhooks;

import com.magentamause.cosybackend.entities.GameServerWebhookEntity;
import com.magentamause.cosybackend.entities.WebhookType;

public interface WebhookSender {
    boolean supports(WebhookType type);

    void send(GameServerWebhookEntity webhook, GameServerDomainEvent event);
}
