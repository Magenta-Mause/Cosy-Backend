package com.magentamause.cosybackend.services.core.gameserver.webhookSender.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.magentamause.cosybackend.entities.GameServerEventType;
import com.magentamause.cosybackend.entities.WebhookEntity;
import com.magentamause.cosybackend.entities.WebhookHttpMethod;
import com.magentamause.cosybackend.entities.WebhookType;
import com.magentamause.cosybackend.services.core.gameserver.webhookSender.GameServerDomainEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Exercises what a custom webhook actually puts on the wire. The WebClient is backed by a capturing
 * {@link ExchangeFunction} instead of a real server, so the assertions can inspect the fully built
 * request without any network or extra test dependency.
 */
class CustomWebhookSenderTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-07-25T10:15:30Z");
    private static final GameServerDomainEvent EVENT =
            new GameServerDomainEvent(
                    "server-uuid", "My Server", GameServerEventType.SERVER_STARTED);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();
    private CustomWebhookSender sender;

    @BeforeEach
    void setUp() {
        ExchangeFunction exchangeFunction =
                request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                };
        sender =
                new CustomWebhookSender(
                        WebClient.builder().exchangeFunction(exchangeFunction).build(),
                        Clock.fixed(TIMESTAMP, ZoneOffset.UTC));
    }

    @Test
    void supportsOnlyCustomWebhooks() {
        assertThat(sender.supports(WebhookType.CUSTOM)).isTrue();
        assertThat(sender.supports(WebhookType.DISCORD)).isFalse();
    }

    @Test
    void sendsConfiguredMethodHeadersAndResolvedBody() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer token");
        headers.put("X-Server", "{{server_name}}");

        sender.send(
                webhook(
                        WebhookHttpMethod.PUT,
                        "{\"event\": \"{{event_name}}\", \"at\": \"{{timestamp}}\"}",
                        headers),
                EVENT);

        MockClientHttpRequest request = writtenRequest();
        assertThat(request.getMethod()).isEqualTo(HttpMethod.PUT);
        assertThat(request.getURI()).hasToString("https://example.org/hook");
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer token");
        assertThat(request.getHeaders().getFirst("X-Server")).isEqualTo("My Server");
        assertThat(body(request))
                .isEqualTo("{\"event\": \"SERVER_STARTED\", \"at\": \"2026-07-25T10:15:30Z\"}");
    }

    @Test
    void defaultsToJsonContentTypeAndEscapesSubstitutedValues() {
        GameServerDomainEvent quoted =
                new GameServerDomainEvent(
                        "server-uuid", "say \"hi\"", GameServerEventType.SERVER_STARTED);

        sender.send(
                webhook(WebhookHttpMethod.POST, "{\"text\": \"{{server_name}}\"}", Map.of()),
                quoted);

        MockClientHttpRequest request = writtenRequest();
        assertThat(request.getHeaders().getContentType()).hasToString("application/json");
        // Without escaping this body would be malformed JSON, because the server name is
        // user-controlled and contains quotes.
        assertThat(body(request)).isEqualTo("{\"text\": \"say \\\"hi\\\"\"}");
    }

    @Test
    void keepsAnExplicitContentTypeAndSkipsJsonEscapingForIt() {
        sender.send(
                webhook(
                        WebhookHttpMethod.POST,
                        "text={{server_name}}",
                        Map.of(HttpHeaders.CONTENT_TYPE, "text/plain")),
                new GameServerDomainEvent(
                        "server-uuid", "a\"b", GameServerEventType.SERVER_STARTED));

        MockClientHttpRequest request = writtenRequest();
        assertThat(request.getHeaders().getContentType()).hasToString("text/plain");
        assertThat(body(request)).isEqualTo("text=a\"b");
    }

    @Test
    void sendsNoBodyForGet() {
        sender.send(webhook(WebhookHttpMethod.GET, "{\"text\": \"{{message}}\"}", Map.of()), EVENT);

        MockClientHttpRequest request = writtenRequest();
        assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(body(request)).isEmpty();
        assertThat(request.getHeaders().getContentType()).isNull();
    }

    @Test
    void stripsLineBreaksFromResolvedHeaderValues() {
        GameServerDomainEvent multiline =
                new GameServerDomainEvent(
                        "server-uuid",
                        "evil\r\nX-Injected: yes",
                        GameServerEventType.SERVER_STARTED);

        sender.send(
                webhook(WebhookHttpMethod.POST, null, Map.of("X-Server", "{{server_name}}")),
                multiline);

        MockClientHttpRequest request = writtenRequest();
        assertThat(request.getHeaders().getFirst("X-Server")).isEqualTo("evilX-Injected: yes");
        assertThat(request.getHeaders().getFirst("X-Injected")).isNull();
    }

    @Test
    void sendsNoBodyWhenNoTemplateIsConfigured() {
        sender.send(webhook(WebhookHttpMethod.POST, "  ", Map.of()), EVENT);

        assertThat(body(writtenRequest())).isEmpty();
    }

    @Test
    void doesNotThrowOnAnUnusableUrl() {
        WebhookEntity webhook = webhook(WebhookHttpMethod.POST, "{}", Map.of());
        webhook.setWebhookUrl("h t t p://nope");

        sender.send(webhook, EVENT);

        // The dispatch loop must survive a webhook it cannot send; nothing was put on the wire.
        assertThat(captured.get()).isNull();
    }

    private static WebhookEntity webhook(
            WebhookHttpMethod method, String bodyTemplate, Map<String, String> headers) {
        return WebhookEntity.builder()
                .uuid("webhook-uuid")
                .webhookType(WebhookType.CUSTOM)
                .webhookUrl("https://example.org/hook")
                .enabled(true)
                .subscribedEvents(Set.of(GameServerEventType.SERVER_STARTED))
                .httpMethod(method)
                .bodyTemplate(bodyTemplate)
                .headers(new LinkedHashMap<>(headers))
                .build();
    }

    /** Materialises the captured {@link ClientRequest} so headers and body can be asserted on. */
    private MockClientHttpRequest writtenRequest() {
        ClientRequest request = captured.get();
        assertThat(request).as("captured request").isNotNull();

        MockClientHttpRequest written = new MockClientHttpRequest(request.method(), request.url());
        request.writeTo(written, ExchangeStrategies.withDefaults()).block();
        return written;
    }

    private static String body(MockClientHttpRequest request) {
        return request.getBodyAsString().block();
    }
}
