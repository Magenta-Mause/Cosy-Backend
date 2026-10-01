package com.magentamause.cosybackend.dtos.actiondtos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.entities.GameServerEventType;
import com.magentamause.cosybackend.entities.WebhookEntity;
import com.magentamause.cosybackend.entities.WebhookHttpMethod;
import com.magentamause.cosybackend.entities.WebhookType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.HashMap;
import java.util.Map;
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

    /** Only meaningful for {@link WebhookType#CUSTOM}; ignored for the built-in integrations. */
    private WebhookHttpMethod httpMethod;

    private String bodyTemplate;
    private Map<String, String> headers;

    @JsonIgnore
    @AssertTrue(message = "httpMethod is required for CUSTOM webhooks")
    public boolean isHttpMethodValid() {
        return CustomWebhookValidation.isHttpMethodValid(webhookType, httpMethod);
    }

    @JsonIgnore
    @AssertTrue(message = "bodyTemplate is too long")
    public boolean isBodyTemplateValid() {
        return CustomWebhookValidation.isBodyTemplateValid(bodyTemplate);
    }

    @JsonIgnore
    @AssertTrue(message = "headers contain an invalid or reserved header name or value")
    public boolean isHeadersValid() {
        return CustomWebhookValidation.areHeadersValid(headers);
    }

    public WebhookEntity toEntity() {
        boolean custom = this.webhookType == WebhookType.CUSTOM;
        return WebhookEntity.builder()
                .webhookType(this.webhookType)
                .webhookUrl(this.webhookUrl)
                .enabled(Objects.requireNonNullElse(this.enabled, true))
                .subscribedEvents(this.subscribedEvents)
                // The custom request format is stored only for CUSTOM webhooks, so a webhook of a
                // built-in type can never carry a stale method/body/headers.
                .httpMethod(custom ? this.httpMethod : null)
                .bodyTemplate(custom ? this.bodyTemplate : null)
                .headers(
                        custom && this.headers != null
                                ? new HashMap<>(this.headers)
                                : new HashMap<>())
                .build();
    }
}
