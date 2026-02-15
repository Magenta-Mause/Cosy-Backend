package com.magentamause.cosybackend.services.core.gameserver.webhookSender.impl;

import com.magentamause.cosybackend.entities.WebhookType;
import com.magentamause.cosybackend.services.core.gameserver.webhookSender.WebhookSender;

public class N8nWebhookSender implements WebhookSender {
    @Override
    public boolean supports(WebhookType type) {
        return type == WebhookType.N8N;
    }
}
