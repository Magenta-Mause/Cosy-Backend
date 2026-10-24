package com.magentamause.cosybackend.services.core.gameserver.webhookSender;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code {{placeholder}}} tokens in the header values and body template of a custom
 * webhook.
 *
 * <p>Unknown placeholders are left in the output verbatim. Silently blanking them would make a typo
 * ({@code {{server_naem}}}) look like an empty value from Cosy, whereas an untouched token is
 * immediately recognisable as a template mistake in the receiving system.
 */
public final class WebhookPlaceholderResolver {

    public static final String EVENT_NAME = "event_name";
    public static final String SERVER_ID = "server_id";
    public static final String SERVER_NAME = "server_name";
    public static final String MESSAGE = "message";
    public static final String TIMESTAMP = "timestamp";

    /** Whitespace inside the braces is tolerated: {@code {{ server_name }}} works too. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    /** How a substituted value has to be escaped to stay valid inside the surrounding document. */
    public enum Escaping {
        NONE,
        /** Escapes the value for use inside a JSON string literal. */
        JSON
    }

    private WebhookPlaceholderResolver() {}

    /** The placeholder values available for one dispatched event. */
    public static Map<String, String> valuesFor(GameServerDomainEvent event, Instant timestamp) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(EVENT_NAME, event.eventType().name());
        values.put(SERVER_ID, event.serverId());
        values.put(SERVER_NAME, event.serverName());
        values.put(MESSAGE, WebhookMessages.forEvent(event));
        values.put(TIMESTAMP, timestamp.toString());
        return values;
    }

    public static String resolve(String template, Map<String, String> values, Escaping escaping) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = values.get(name);
            String replacement = value == null ? matcher.group() : escape(value, escaping);
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private static String escape(String value, Escaping escaping) {
        return escaping == Escaping.JSON ? escapeJson(value) : value;
    }

    /**
     * Escapes a value for a JSON string literal. Server names are user-controlled, so a name
     * containing a quote or newline would otherwise turn a valid body template into malformed JSON
     * that the receiver rejects.
     */
    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
