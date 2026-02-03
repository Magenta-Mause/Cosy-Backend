package com.magentamause.cosybackend.configs;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.magentamause.cosybackend.configs.properties.EngineProperties;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EngineProperties.class)
public class EngineConfiguration {

    @Bean
    public DockerClient dockerClient(EngineProperties properties) {

        EngineProperties.Docker cfg = properties.docker();
        if (cfg == null) {
            throw new IllegalStateException("Docker engine selected but docker config missing");
        }

        var dockerConfig =
                DefaultDockerClientConfig.createDefaultConfigBuilder()
                        .withDockerHost(cfg.socketPath())
                        .withDockerTlsVerify(cfg.tls())
                        .withDockerCertPath(cfg.certPath())
                        .withApiVersion(cfg.apiVersion())
                        .build();

        DockerHttpClient httpClient =
                new ZerodepDockerHttpClient.Builder()
                        .dockerHost(dockerConfig.getDockerHost())
                        .sslConfig(dockerConfig.getSSLConfig())
                        .maxConnections(100)
                        .connectionTimeout(Duration.ofSeconds(30))
                        .responseTimeout(Duration.ofSeconds(45))
                        .build();

        return DockerClientImpl.getInstance(dockerConfig, httpClient);
    }
}
