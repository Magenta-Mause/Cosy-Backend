package com.magentamause.cosybackend.configs.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cosy.loki")
public record LokiProperties(
        String username, String password, String url, String applicationName) {}
