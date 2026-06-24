package com.magentamause.cosybackend.configs.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Template-service endpoints. {@code url} points at {@code /v3/templates}; {@code gamesUrl} points
 * at the sibling {@code /v3/games} endpoint (games are now sourced from the template-service rather
 * than the legacy SteamGridDB game-api).
 */
@ConfigurationProperties(prefix = "cosy.templates-api")
public record CosyTemplateApiProperties(String url, String gamesUrl) {}
