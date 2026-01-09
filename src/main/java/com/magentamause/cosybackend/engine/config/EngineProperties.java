package com.magentamause.cosybackend.engine.config;

import com.magentamause.cosybackend.engine.EngineType;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cosy.engine")
public record EngineProperties(EngineType selected, Docker docker, Kubernetes kubernetes) {

    public record Docker(String socketPath, String apiVersion, boolean tls, String certPath) {}

    public record Kubernetes(
            String kubeconfig,
            String context,
            String namespace,
            boolean inCluster,
            int timeoutSeconds,
            Map<String, String> labels) {}
}
