package com.magentamause.cosybackend.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cosy.games-api")
public record GamesApiProperties(String url) {}
