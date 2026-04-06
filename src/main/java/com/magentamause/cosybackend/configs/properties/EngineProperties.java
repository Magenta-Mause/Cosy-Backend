package com.magentamause.cosybackend.configs.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cosy.engine")
public record EngineProperties(List<Integer> blockedPorts, Docker docker) {

    public record Docker(
            String socketPath,
            String apiVersion,
            boolean tls,
            String certPath,
            String volumeDirectory,
            String containerNamePrefix,
            String inBackendVolumeMountPath,
            String networkName) {}
}
