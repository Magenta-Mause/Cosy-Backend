package com.magentamause.cosybackend.services.core.gameserver.webhookSender;

import static org.assertj.core.api.Assertions.assertThat;

import com.magentamause.cosybackend.entities.GameServerEventType;
import com.magentamause.cosybackend.services.core.gameserver.webhookSender.WebhookPlaceholderResolver.Escaping;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebhookPlaceholderResolverTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-07-25T10:15:30Z");

    private static Map<String, String> values(String serverName) {
        return WebhookPlaceholderResolver.valuesFor(
                new GameServerDomainEvent(
                        "server-uuid", serverName, GameServerEventType.SERVER_STARTED),
                TIMESTAMP);
    }

    @Test
    void resolvesEveryDocumentedPlaceholder() {
        String template = "{{event_name}}|{{server_id}}|{{server_name}}|{{message}}|{{timestamp}}";

        String resolved =
                WebhookPlaceholderResolver.resolve(template, values("My Server"), Escaping.NONE);

        assertThat(resolved)
                .isEqualTo(
                        "SERVER_STARTED|server-uuid|My Server|Server started: My Server"
                                + "|2026-07-25T10:15:30Z");
    }

    @Test
    void toleratesWhitespaceInsidePlaceholderBraces() {
        String resolved =
                WebhookPlaceholderResolver.resolve(
                        "{{ server_name }}", values("My Server"), Escaping.NONE);

        assertThat(resolved).isEqualTo("My Server");
    }

    @Test
    void leavesUnknownPlaceholdersUntouched() {
        // A typo has to stay visible in the delivered payload rather than being silently blanked,
        // otherwise it is indistinguishable from Cosy sending an empty value.
        String resolved =
                WebhookPlaceholderResolver.resolve(
                        "{{server_naem}} was {{event_name}}", values("My Server"), Escaping.NONE);

        assertThat(resolved).isEqualTo("{{server_naem}} was SERVER_STARTED");
    }

    @Test
    void escapesQuotesAndControlCharactersForJsonBodies() {
        String resolved =
                WebhookPlaceholderResolver.resolve(
                        "{\"text\": \"{{server_name}}\"}",
                        values("say \"hi\"\nnow"),
                        Escaping.JSON);

        assertThat(resolved).isEqualTo("{\"text\": \"say \\\"hi\\\"\\nnow\"}");
    }

    @Test
    void doesNotEscapeWhenTheBodyIsNotJson() {
        String resolved =
                WebhookPlaceholderResolver.resolve(
                        "name={{server_name}}", values("a\"b"), Escaping.NONE);

        assertThat(resolved).isEqualTo("name=a\"b");
    }

    @Test
    void treatsDollarAndBackslashInValuesAsLiteralText() {
        // Regex replacement syntax must not leak: a server named "$1" or "a\b" would otherwise
        // throw or produce garbage during substitution.
        String resolved =
                WebhookPlaceholderResolver.resolve(
                        "{{server_name}}", values("$1 and \\x"), Escaping.NONE);

        assertThat(resolved).isEqualTo("$1 and \\x");
    }

    @Test
    void returnsBlankTemplatesUnchanged() {
        assertThat(WebhookPlaceholderResolver.resolve(null, values("s"), Escaping.JSON)).isNull();
        assertThat(WebhookPlaceholderResolver.resolve("", values("s"), Escaping.JSON)).isEmpty();
    }
}
