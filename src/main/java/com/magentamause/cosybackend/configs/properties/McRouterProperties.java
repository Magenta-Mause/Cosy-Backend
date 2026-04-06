package com.magentamause.cosybackend.configs.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cosy.mc-router")
public record McRouterProperties(String image, String containerName, int defaultPort) {}
