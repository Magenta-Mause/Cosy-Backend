package com.magentamause.cosybackend.services.core.gameserver.webhookSender.impl;

import com.magentamause.cosybackend.entities.WebhookEntity;
import com.magentamause.cosybackend.entities.WebhookHttpMethod;
import com.magentamause.cosybackend.entities.WebhookType;
import com.magentamause.cosybackend.services.core.gameserver.webhookSender.GameServerDomainEvent;
import com.magentamause.cosybackend.services.core.gameserver.webhookSender.WebhookPlaceholderResolver;
import com.magentamause.cosybackend.services.core.gameserver.webhookSender.WebhookPlaceholderResolver.Escaping;
import com.magentamause.cosybackend.services.core.gameserver.webhookSender.WebhookSender;
import java.net.URI;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Sends a webhook in the format the user defined: their own HTTP method, headers and body template,
 * with {@code {{placeholder}}} tokens resolved from the event.
 *
 * <p>Unlike {@link BaseWebhookSender}, which owns the payload shape for each built-in integration,
 * nothing here is fixed except the target URL — so this sender builds the request from scratch
 * instead of extending that base class.
 */
@Slf4j
@Component
public class CustomWebhookSender implements WebhookSender {

    /** Used when a webhook somehow has no method stored; every CUSTOM webhook is validated to. */
    private static final WebhookHttpMethod DEFAULT_METHOD = WebhookHttpMethod.POST;

    private final WebClient webClient;
    private final Clock clock;

    @Autowired
    public CustomWebhookSender(@Qualifier("webhookWebClient") WebClient webClient) {
        this(webClient, Clock.systemUTC());
    }

    /** Visible for tests, which pin the clock so {@code {{timestamp}}} is deterministic. */
    CustomWebhookSender(WebClient webClient, Clock clock) {
        this.webClient = webClient;
        this.clock = clock;
    }

    @Override
    public boolean supports(WebhookType type) {
        return type == WebhookType.CUSTOM;
    }

    @Override
    public void send(WebhookEntity webhook, GameServerDomainEvent event) {
        try {
            WebhookHttpMethod method =
                    webhook.getHttpMethod() == null ? DEFAULT_METHOD : webhook.getHttpMethod();
            Map<String, String> values =
                    WebhookPlaceholderResolver.valuesFor(event, clock.instant());
            Map<String, String> headers = resolveHeaders(webhook.getHeaders(), values);
            String body = resolveBody(webhook.getBodyTemplate(), method, headers, values);

            WebClient.RequestBodySpec request =
                    webClient
                            // URI.create instead of the String overload: the latter treats the URL
                            // as a URI template, so braces in a user-supplied URL would be read as
                            // template variables.
                            .method(HttpMethod.valueOf(method.name()))
                            .uri(URI.create(webhook.getWebhookUrl()))
                            .headers(httpHeaders -> headers.forEach(httpHeaders::set));

            if (body == null) {
                subscribe(request, webhook, event);
                return;
            }

            if (!hasContentType(headers)) {
                request.contentType(MediaType.APPLICATION_JSON);
            }
            subscribe(request.bodyValue(body), webhook, event);
        } catch (Exception ex) {
            log.error(
                    "Failed to prepare custom webhook request for server {} and event {}",
                    event.serverId(),
                    event.eventType(),
                    ex);
        }
    }

    private void subscribe(
            WebClient.RequestHeadersSpec<?> request,
            WebhookEntity webhook,
            GameServerDomainEvent event) {
        request.retrieve()
                .toBodilessEntity()
                .doOnError(
                        ex ->
                                log.error(
                                        "Failed to send custom webhook {} for server {} and event"
                                                + " {}",
                                        webhook.getUuid(),
                                        event.serverId(),
                                        event.eventType(),
                                        ex))
                .subscribe();
    }

    /**
     * Header values are resolved without escaping — they are not JSON — but CR and LF are dropped,
     * because a placeholder value carrying a line break would otherwise let a server name inject
     * additional headers.
     */
    private static Map<String, String> resolveHeaders(
            Map<String, String> headerTemplates, Map<String, String> values) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (headerTemplates == null) {
            return headers;
        }
        headerTemplates.forEach(
                (name, template) -> {
                    String resolved =
                            WebhookPlaceholderResolver.resolve(template, values, Escaping.NONE);
                    headers.put(name, stripLineBreaks(resolved));
                });
        return headers;
    }

    /**
     * Resolves the body template, escaping substituted values as JSON when the request is sent as
     * JSON -- the default content type, and the format nearly every webhook receiver expects.
     */
    private static String resolveBody(
            String bodyTemplate,
            WebhookHttpMethod method,
            Map<String, String> headers,
            Map<String, String> values) {
        if (!method.allowsBody() || bodyTemplate == null || bodyTemplate.isBlank()) {
            return null;
        }
        return WebhookPlaceholderResolver.resolve(bodyTemplate, values, escapingFor(headers));
    }

    private static Escaping escapingFor(Map<String, String> headers) {
        String contentType = contentType(headers);
        boolean json = contentType == null || contentType.toLowerCase(Locale.ROOT).contains("json");
        return json ? Escaping.JSON : Escaping.NONE;
    }

    private static boolean hasContentType(Map<String, String> headers) {
        return contentType(headers) != null;
    }

    private static String contentType(Map<String, String> headers) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(HttpHeaders.CONTENT_TYPE))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static String stripLineBreaks(String value) {
        return value == null ? null : value.replace("\r", "").replace("\n", "");
    }
}
