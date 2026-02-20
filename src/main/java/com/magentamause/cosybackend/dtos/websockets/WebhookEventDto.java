package com.magentamause.cosybackend.dtos.websockets;

import com.magentamause.cosybackend.dtos.entitydtos.WebhookDto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WebhookEventDto(String serverUuid, WebhookDto webhook) {}
