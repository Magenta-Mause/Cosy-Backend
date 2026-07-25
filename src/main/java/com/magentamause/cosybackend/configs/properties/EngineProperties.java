package com.magentamause.cosybackend.configs.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "cosy.engine")
public record EngineProperties(Docker docker, @DefaultValue Reconciliation reconciliation) {

    public record Docker(
            String socketPath,
            String apiVersion,
            boolean tls,
            String certPath,
            String volumeDirectory,
            String containerNamePrefix,
            String inBackendVolumeMountPath) {}

    /**
     * Settings of the periodic safety net that re-derives the persisted game server status from the
     * real container state, so a status event that was never delivered cannot leave a server stuck
     * in a transitional status.
     *
     * @param intervalMs how often the sweep runs
     * @param gracePeriodMs how long a server that recently entered a transitional status is left
     *     alone, so the sweep cannot fight an in-flight start or stop
     */
    public record Reconciliation(
            @DefaultValue(DEFAULT_INTERVAL_MS) long intervalMs,
            @DefaultValue(DEFAULT_GRACE_PERIOD_MS) long gracePeriodMs) {

        public static final String DEFAULT_INTERVAL_MS = "180000";
        public static final String DEFAULT_GRACE_PERIOD_MS = "60000";
    }
}
