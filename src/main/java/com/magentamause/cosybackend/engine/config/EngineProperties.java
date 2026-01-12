package com.magentamause.cosybackend.engine.config;

import com.magentamause.cosybackend.engine.EngineType;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cosy.engine")
public record EngineProperties(EngineType selected, Docker docker) {

    public record Docker(String socketPath, String apiVersion, boolean tls, String certPath) {}
}

