package com.magentamause.cosybackend.services.core.gameserver.webhookSender.impl;

import com.magentamause.cosybackend.entities.WebhookType;
import com.magentamause.cosybackend.services.core.gameserver.webhookSender.WebhookSender;

public class SlackWebhookSender implements WebhookSender {
    @Override
    public boolean supports(WebhookType type) {
        return type == WebhookType.SLACK;
    }
}
