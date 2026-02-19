package com.magentamause.cosybackend.dtos.websockets;

import com.magentamause.cosybackend.dtos.entitydtos.WebhookDto;

public record WebhookEventDto(String eventType, String serverUuid, WebhookDto webhook) {}
