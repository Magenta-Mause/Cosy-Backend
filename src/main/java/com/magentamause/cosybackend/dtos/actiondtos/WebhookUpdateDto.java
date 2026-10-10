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

    public void applyToEntity(WebhookEntity webhookEntity) {
        webhookEntity.setWebhookType(webhookType);
        webhookEntity.setWebhookUrl(webhookUrl);
        webhookEntity.setEnabled(enabled);
        webhookEntity.setSubscribedEvents(subscribedEvents);

        // Switching away from CUSTOM clears the request format instead of leaving it dormant in the
        // database, so switching back later cannot silently resurrect an old configuration.
        boolean custom = webhookType == WebhookType.CUSTOM;
        webhookEntity.setHttpMethod(custom ? httpMethod : null);
        webhookEntity.setBodyTemplate(custom ? bodyTemplate : null);
        replaceHeaders(webhookEntity, custom && headers != null ? headers : Map.of());
    }

    /**
     * Updates the entity's header map in place rather than swapping in a new instance: Hibernate
     * tracks the collection instance of an {@code @ElementCollection}, and replacing it on a
     * managed entity makes it delete and re-insert every row.
     */
    private static void replaceHeaders(
            WebhookEntity webhookEntity, Map<String, String> newHeaders) {
        Map<String, String> current = webhookEntity.getHeaders();
        if (current == null) {
            webhookEntity.setHeaders(new HashMap<>(newHeaders));
            return;
        }
        current.keySet().retainAll(newHeaders.keySet());
        current.putAll(newHeaders);
    }
}
