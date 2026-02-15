package com.magentamause.cosybackend.dtos.actiondtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.GameServerEventType;
import com.magentamause.cosybackend.entities.WebhookEntity;
import com.magentamause.cosybackend.entities.WebhookType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @NotBlank private String webhookUrl;
    private boolean enabled;
    @NotNull private Set<GameServerEventType> subscribedEvents;

    public WebhookEntity toEntity(GameServerEntity gameServer) {
        return WebhookEntity.builder()
                .gameServer(gameServer)
                .webhookType(this.webhookType)
                .webhookUrl(this.webhookUrl)
                .enabled(this.enabled)
                .subscribedEvents(this.subscribedEvents)
                .build();
    }
}
