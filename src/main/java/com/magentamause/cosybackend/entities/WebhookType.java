package com.magentamause.cosybackend.entities;

public enum WebhookType {
    DISCORD,
    SLACK,
    N8N,
    /**
     * User-defined request format: the webhook carries its own HTTP method, headers and body
     * template instead of one of the built-in integration payloads.
     */
    CUSTOM
}
