package com.magentamause.cosybackend.configs.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cosy.engine")
public record EngineProperties(Docker docker) {

    public record Docker(
            String socketPath,
            String apiVersion,
            boolean tls,
            String certPath,
            String volumeDirectory,
            String containerNamePrefix) {}
}
