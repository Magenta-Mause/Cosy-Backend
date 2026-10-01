package com.magentamause.cosybackend.entities;

/**
 * HTTP method a {@link WebhookType#CUSTOM} webhook is sent with. Only CUSTOM webhooks carry one --
 * the built-in integrations (Discord, Slack, n8n) always POST.
 */
public enum WebhookHttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    HEAD,
    OPTIONS;

    /**
     * Whether a request body is sent for this method. GET and HEAD are the two methods where a body
     * is meaningless (and rejected by many servers), so a configured body template is skipped for
     * them.
     */
    public boolean allowsBody() {
        return this != GET && this != HEAD;
    }
}
