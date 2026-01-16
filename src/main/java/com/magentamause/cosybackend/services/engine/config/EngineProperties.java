package com.magentamause.cosybackend.services.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cosy.engine")
public record EngineProperties(Docker docker) {

    public record Docker(String socketPath, String apiVersion, boolean tls, String certPath) {}
}
