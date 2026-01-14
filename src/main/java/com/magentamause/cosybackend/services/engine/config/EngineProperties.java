package com.magentamause.cosybackend.services.engine.config;

import com.magentamause.cosybackend.services.engine.EngineType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cosy.engine")
public record EngineProperties(EngineType selected, Docker docker) {

    public record Docker(String socketPath, String apiVersion, boolean tls, String certPath) {}
}
