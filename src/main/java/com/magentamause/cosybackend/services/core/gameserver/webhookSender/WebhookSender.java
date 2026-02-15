package com.magentamause.cosybackend.services.core.gameserver.webhookSender;

import com.magentamause.cosybackend.entities.WebhookEntity;
import com.magentamause.cosybackend.entities.WebhookType;

public interface WebhookSender {
    boolean supports(WebhookType type);

    default void send(WebhookEntity webhook, GameServerDomainEvent event) throws Exception {
        throw new Exception("Webhook sender not implemented");
    }
}
