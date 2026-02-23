package com.magentamause.cosybackend.dtos.actiondtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.entities.GameServerEventType;
import com.magentamause.cosybackend.entities.WebhookEntity;
import com.magentamause.cosybackend.entities.WebhookType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Set;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookUpdateDto {
    @NotNull private WebhookType webhookType;

    @NotBlank
    @Pattern(
            regexp = "https?://.+",
            flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "webhookUrl must be an absolute http(s) URL")
    private String webhookUrl;

    @NotNull private Boolean enabled;
    @NotNull private Set<GameServerEventType> subscribedEvents;

    public void applyToEntity(WebhookEntity webhookEntity) {
        webhookEntity.setWebhookType(webhookType);
        webhookEntity.setWebhookUrl(webhookUrl);
        webhookEntity.setEnabled(enabled);
        webhookEntity.setSubscribedEvents(subscribedEvents);
    }
}
