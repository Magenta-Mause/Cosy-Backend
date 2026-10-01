package com.magentamause.cosybackend.dtos.entitydtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.entities.GameServerEventType;
import com.magentamause.cosybackend.entities.WebhookHttpMethod;
import com.magentamause.cosybackend.entities.WebhookType;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class WebhookDto {
    private String uuid;
    private WebhookType webhookType;
    private String webhookUrl;
    private boolean enabled;
    private Set<GameServerEventType> subscribedEvents;

    /** Custom request format; null/empty for the built-in integration types. */
    private WebhookHttpMethod httpMethod;

    private String bodyTemplate;
    private Map<String, String> headers;
}
