package com.magentamause.cosybackend.dtos.actiondtos;

import static org.assertj.core.api.Assertions.assertThat;

import com.magentamause.cosybackend.entities.GameServerEventType;
import com.magentamause.cosybackend.entities.WebhookEntity;
import com.magentamause.cosybackend.entities.WebhookHttpMethod;
import com.magentamause.cosybackend.entities.WebhookType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Validation and entity mapping of the custom request format on the webhook action DTOs. */
class CustomWebhookDtoTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void customWebhookWithoutMethodIsRejected() {
        WebhookCreationDto dto = creationDto(WebhookType.CUSTOM, null, null, null);

        assertThat(violatedProperties(dto)).contains("httpMethodValid");
    }

    @Test
    void customWebhookWithMethodIsAccepted() {
        WebhookCreationDto dto =
                creationDto(
                        WebhookType.CUSTOM,
                        WebhookHttpMethod.POST,
                        "{\"text\": \"{{message}}\"}",
                        Map.of("X-Token", "secret"));

        assertThat(violatedProperties(dto)).isEmpty();
    }

    @Test
    void builtInWebhookWithoutMethodStaysValid() {
        WebhookCreationDto dto = creationDto(WebhookType.DISCORD, null, null, null);

        assertThat(violatedProperties(dto)).isEmpty();
    }

    @Test
    void transportOwnedHeadersAreRejected() {
        WebhookCreationDto dto =
                creationDto(
                        WebhookType.CUSTOM,
                        WebhookHttpMethod.POST,
                        null,
                        Map.of("Content-Length", "12"));

        assertThat(violatedProperties(dto)).contains("headersValid");
    }

    @Test
    void headerValuesWithLineBreaksAreRejected() {
        WebhookCreationDto dto =
                creationDto(
                        WebhookType.CUSTOM,
                        WebhookHttpMethod.POST,
                        null,
                        Map.of("X-Token", "a\r\nX-Injected: yes"));

        assertThat(violatedProperties(dto)).contains("headersValid");
    }

    @Test
    void headerNamesThatAreNotHttpTokensAreRejected() {
        WebhookCreationDto dto =
                creationDto(
                        WebhookType.CUSTOM, WebhookHttpMethod.POST, null, Map.of("X Token", "v"));

        assertThat(violatedProperties(dto)).contains("headersValid");
    }

    @Test
    void tooManyHeadersAreRejected() {
        Map<String, String> headers = new HashMap<>();
        for (int i = 0; i <= CustomWebhookValidation.MAX_HEADERS; i++) {
            headers.put("X-Header-" + i, "value");
        }
        WebhookCreationDto dto =
                creationDto(WebhookType.CUSTOM, WebhookHttpMethod.POST, null, headers);

        assertThat(violatedProperties(dto)).contains("headersValid");
    }

    @Test
    void overlongBodyTemplateIsRejected() {
        WebhookCreationDto dto =
                creationDto(
                        WebhookType.CUSTOM,
                        WebhookHttpMethod.POST,
                        "x".repeat(CustomWebhookValidation.MAX_BODY_TEMPLATE_LENGTH + 1),
                        null);

        assertThat(violatedProperties(dto)).contains("bodyTemplateValid");
    }

    @Test
    void creationDropsCustomFieldsForBuiltInTypes() {
        WebhookCreationDto dto =
                creationDto(
                        WebhookType.SLACK,
                        WebhookHttpMethod.PATCH,
                        "{{message}}",
                        Map.of("X-Token", "secret"));

        WebhookEntity entity = dto.toEntity();

        assertThat(entity.getHttpMethod()).isNull();
        assertThat(entity.getBodyTemplate()).isNull();
        assertThat(entity.getHeaders()).isEmpty();
    }

    @Test
    void updateClearsCustomFieldsWhenSwitchingAwayFromCustom() {
        WebhookEntity entity = customEntity();

        WebhookUpdateDto dto = updateDto(WebhookType.DISCORD, null, null, null);
        dto.applyToEntity(entity);

        assertThat(entity.getHttpMethod()).isNull();
        assertThat(entity.getBodyTemplate()).isNull();
        assertThat(entity.getHeaders()).isEmpty();
    }

    @Test
    void updateReusesTheExistingHeaderMapInstance() {
        WebhookEntity entity = customEntity();
        Map<String, String> managedHeaders = entity.getHeaders();

        WebhookUpdateDto dto =
                updateDto(
                        WebhookType.CUSTOM,
                        WebhookHttpMethod.POST,
                        "{{message}}",
                        Map.of("X-New", "value"));
        dto.applyToEntity(entity);

        // Hibernate tracks the collection instance of an @ElementCollection; swapping it out would
        // make every update delete and re-insert all header rows.
        assertThat(entity.getHeaders()).isSameAs(managedHeaders).containsExactly(entry());
    }

    private static Map.Entry<String, String> entry() {
        return Map.entry("X-New", "value");
    }

    private static WebhookEntity customEntity() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Old", "old");
        return WebhookEntity.builder()
                .uuid("webhook-uuid")
                .webhookType(WebhookType.CUSTOM)
                .webhookUrl("https://example.org/hook")
                .enabled(true)
                .subscribedEvents(Set.of(GameServerEventType.SERVER_STARTED))
                .httpMethod(WebhookHttpMethod.PUT)
                .bodyTemplate("{{message}}")
                .headers(headers)
                .build();
    }

    private static WebhookCreationDto creationDto(
            WebhookType type,
            WebhookHttpMethod method,
            String bodyTemplate,
            Map<String, String> headers) {
        return WebhookCreationDto.builder()
                .webhookType(type)
                .webhookUrl("https://example.org/hook")
                .enabled(true)
                .subscribedEvents(Set.of(GameServerEventType.SERVER_STARTED))
                .httpMethod(method)
                .bodyTemplate(bodyTemplate)
                .headers(headers)
                .build();
    }

    private static WebhookUpdateDto updateDto(
            WebhookType type,
            WebhookHttpMethod method,
            String bodyTemplate,
            Map<String, String> headers) {
        WebhookUpdateDto dto = new WebhookUpdateDto();
        dto.setWebhookType(type);
        dto.setWebhookUrl("https://example.org/hook");
        dto.setEnabled(true);
        dto.setSubscribedEvents(Set.of(GameServerEventType.SERVER_STARTED));
        dto.setHttpMethod(method);
        dto.setBodyTemplate(bodyTemplate);
        dto.setHeaders(headers);
        return dto;
    }

    private static Set<String> violatedProperties(Object dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
