package com.magentamause.cosybackend.configs;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.magentamause.cosybackend.configs.properties.EngineProperties;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(EngineProperties.class)
public class EngineConfiguration {

    /**
     * Bean name of the {@link DockerClient} that must be used for long-lived streaming commands
     * (the {@code /events} subscription and container log/stdin attachments).
     */
    public static final String STREAMING_DOCKER_CLIENT = "streamingDockerClient";

    private static final int MAX_CONNECTIONS = 100;
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(45);

    /**
     * Client for one-shot commands (inspect, create, start, stop, stats, ...). These are
     * request/response calls that must not hang forever, so a response (socket read) timeout is
     * appropriate here.
     */
    @Bean
    @Primary
    public DockerClient dockerClient(EngineProperties properties) {
        DockerClientConfig dockerConfig = buildClientConfig(properties);

        DockerHttpClient httpClient =
                baseHttpClientBuilder(dockerConfig).responseTimeout(RESPONSE_TIMEOUT).build();

        return DockerClientImpl.getInstance(dockerConfig, httpClient);
    }

    /**
     * Client for long-lived streaming commands. It deliberately has NO response timeout: the
     * response timeout is a socket read timeout, and a stream that is legitimately quiet (no Docker
     * events, no container output) produces no bytes to read. With a read timeout the connection is
     * torn down after that period of silence, which previously killed the event subscription and
     * container log streams for good. A read timeout must never be applied to a long-lived stream.
     *
     * <p>The connection timeout is kept — establishing the connection to the Docker socket still
     * has to fail fast.
     */
    @Bean(STREAMING_DOCKER_CLIENT)
    public DockerClient streamingDockerClient(EngineProperties properties) {
        DockerClientConfig dockerConfig = buildClientConfig(properties);

        DockerHttpClient httpClient = baseHttpClientBuilder(dockerConfig).build();

        return DockerClientImpl.getInstance(dockerConfig, httpClient);
    }

    private DockerClientConfig buildClientConfig(EngineProperties properties) {
        EngineProperties.Docker cfg = properties.docker();
        if (cfg == null) {
            throw new IllegalStateException("Docker engine selected but docker config missing");
        }

        return DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(cfg.socketPath())
                .withDockerTlsVerify(cfg.tls())
                .withDockerCertPath(cfg.certPath())
                .withApiVersion(cfg.apiVersion())
                .build();
    }

    private ZerodepDockerHttpClient.Builder baseHttpClientBuilder(DockerClientConfig dockerConfig) {
        return new ZerodepDockerHttpClient.Builder()
                .dockerHost(dockerConfig.getDockerHost())
                .sslConfig(dockerConfig.getSSLConfig())
                .maxConnections(MAX_CONNECTIONS)
                .connectionTimeout(CONNECTION_TIMEOUT);
    }
}
