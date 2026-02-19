package com.magentamause.cosybackend.dtos.actiondtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.entities.GameServerEventType;
import com.magentamause.cosybackend.entities.WebhookEntity;
import com.magentamause.cosybackend.entities.WebhookType;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Objects;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookCreationDto {
    @NotNull private WebhookType webhookType;

    @NotBlank
    @Pattern(
            regexp = "https?://.+",
            flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "webhookUrl must be an absolute http(s) URL")
    private String webhookUrl;

    @Builder.Default private Boolean enabled = true;
    @NotNull private Set<GameServerEventType> subscribedEvents;

    public WebhookEntity toEntity(GameServerEntity gameServer) {
        return WebhookEntity.builder()
                .gameServer(gameServer)
                .webhookType(this.webhookType)
                .webhookUrl(this.webhookUrl)
                .enabled(Objects.requireNonNullElse(this.enabled, true))
                .subscribedEvents(this.subscribedEvents)
                .build();
    }
}
