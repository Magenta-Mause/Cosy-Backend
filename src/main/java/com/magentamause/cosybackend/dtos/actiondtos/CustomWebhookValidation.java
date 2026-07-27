package com.magentamause.cosybackend.dtos.actiondtos;

import com.magentamause.cosybackend.entities.WebhookHttpMethod;
import com.magentamause.cosybackend.entities.WebhookType;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared rules for the {@link WebhookType#CUSTOM} request format, used by both {@link
 * WebhookCreationDto} and {@link WebhookUpdateDto} so create and update cannot drift apart.
 */
public final class CustomWebhookValidation {

    public static final int MAX_HEADERS = 20;
    public static final int MAX_HEADER_VALUE_LENGTH = 2048;
    public static final int MAX_BODY_TEMPLATE_LENGTH = 10_000;

    /** RFC 7230 header field-name token characters. */
    private static final Pattern HEADER_NAME =
            Pattern.compile("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+", Pattern.UNICODE_CASE);

    /**
     * Headers the transport owns. Letting a user set these would either be silently overwritten by
     * the HTTP client or actively break the request framing, so they are rejected up front rather
     * than accepted and ignored.
     */
    private static final Set<String> RESERVED_HEADERS =
            Set.of(
                    "host",
                    "content-length",
                    "transfer-encoding",
                    "connection",
                    "upgrade",
                    "expect",
                    "te");

    private CustomWebhookValidation() {}

    /** A CUSTOM webhook must say which method to send; other types never carry one. */
    public static boolean isHttpMethodValid(WebhookType webhookType, WebhookHttpMethod httpMethod) {
        return webhookType != WebhookType.CUSTOM || httpMethod != null;
    }

    public static boolean isBodyTemplateValid(String bodyTemplate) {
        return bodyTemplate == null || bodyTemplate.length() <= MAX_BODY_TEMPLATE_LENGTH;
    }

    /**
     * Header names must be real HTTP tokens and must not be transport-owned; values must fit the
     * column and must not contain CR/LF, which would otherwise allow header injection.
     */
    public static boolean areHeadersValid(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return true;
        }
        if (headers.size() > MAX_HEADERS) {
            return false;
        }
        return headers.entrySet().stream().allMatch(CustomWebhookValidation::isHeaderValid);
    }

    private static boolean isHeaderValid(Map.Entry<String, String> header) {
        String name = header.getKey();
        String value = header.getValue();

        if (name == null || !HEADER_NAME.matcher(name).matches()) {
            return false;
        }
        if (RESERVED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            return false;
        }
        return value != null
                && value.length() <= MAX_HEADER_VALUE_LENGTH
                && value.indexOf('\r') < 0
                && value.indexOf('\n') < 0;
    }
}
