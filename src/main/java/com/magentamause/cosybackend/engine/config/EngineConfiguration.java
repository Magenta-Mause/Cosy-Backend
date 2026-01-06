package com.magentamause.cosybackend.engine.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.magentamause.cosybackend.engine.EngineManager;
import com.magentamause.cosybackend.engine.docker.DockerEngineManager;
import com.magentamause.cosybackend.engine.kubernetes.KubernetesEngineManager;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import java.io.FileReader;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EngineProperties.class)
public class EngineConfiguration {

    /* ---------------- Docker ---------------- */

    @Bean
    @ConditionalOnProperty(name = "cosy.engine.selected", havingValue = "DOCKER")
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
                new ApacheDockerHttpClient.Builder()
                        .dockerHost(dockerConfig.getDockerHost())
                        .sslConfig(dockerConfig.getSSLConfig())
                        .maxConnections(100)
                        .connectionTimeout(Duration.ofSeconds(30))
                        .responseTimeout(Duration.ofSeconds(45))
                        .build();

        return DockerClientImpl.getInstance(dockerConfig, httpClient);
    }

    @Bean
    @ConditionalOnProperty(name = "cosy.engine.selected", havingValue = "DOCKER")
    public EngineManager dockerEngineManager(
            DockerClient dockerClient, EngineProperties properties) {

        return new DockerEngineManager(properties.docker(), dockerClient);
    }

    /* ---------------- Kubernetes ---------------- */

    @Bean
    @ConditionalOnProperty(name = "cosy.engine.selected", havingValue = "KUBERNETES")
    public ApiClient kubernetesApiClient(EngineProperties properties) throws Exception {

        EngineProperties.Kubernetes cfg = properties.kubernetes();
        if (cfg == null) {
            throw new IllegalStateException(
                    "Kubernetes engine selected but kubernetes config missing");
        }

        ApiClient client;

        if (cfg.inCluster()) {
            client = Config.fromCluster();
        } else {
            try (FileReader reader = new FileReader(cfg.kubeconfig())) {
                KubeConfig kubeConfig = KubeConfig.loadKubeConfig(reader);

                // Apply configured context, if present
                if (cfg.context() != null && !cfg.context().isBlank()) {
                    kubeConfig.setContext(cfg.context());
                }

                client = Config.fromConfig(kubeConfig);
            }
        }

        client.setReadTimeout(cfg.timeoutSeconds() * 1000);
        return client;
    }

    @Bean
    @ConditionalOnProperty(name = "cosy.engine.selected", havingValue = "KUBERNETES")
    public CoreV1Api coreV1Api(ApiClient client) {
        return new CoreV1Api(client);
    }

    @Bean
    @ConditionalOnProperty(name = "cosy.engine.selected", havingValue = "KUBERNETES")
    public EngineManager kubernetesEngineManager(CoreV1Api api, EngineProperties properties) {

        return new KubernetesEngineManager(properties.kubernetes(), api);
    }
}
