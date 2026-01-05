package com.magentamause.cosybackend.engine.kubernetes;

import com.magentamause.cosybackend.engine.EngineManager;
import com.magentamause.cosybackend.engine.config.EngineProperties.Kubernetes;
import com.magentamause.cosybackend.entities.GameServerConfigurationEntity;
import com.magentamause.cosybackend.entities.utility.EnvironmentVariableConfiguration;
import com.magentamause.cosybackend.entities.utility.PortMapping;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;

import java.util.*;
import java.util.stream.Collectors;

public class KubernetesEngineManager implements EngineManager {

    private final Kubernetes config;
    private final CoreV1Api api;

    public KubernetesEngineManager(CoreV1Api api, Kubernetes config) {
        this.api = api;
        this.config = config;
    }

    @Override
    public List<Integer> start(GameServerConfigurationEntity server) {
        Optional<V1Pod> existingPod = findPod(server);
        Optional<V1Service> existingService = findService(server);

        if (existingPod.isPresent() && existingService.isPresent()) {
            return getNodePorts(existingService.get());
        }

        V1Pod pod = buildPod(server);
        try {
            existingPod.orElseGet(
                    () -> {
                        createPod(pod);
                        try {
                            waitForPodRunning(podName(server));
                        }
                        catch (ApiException e) {
                            throw new RuntimeException(e);
                        }
                        return pod;
                    });

            existingService.orElseGet(
                    () -> {
                        createService(server);
                        return findService(server).orElseThrow();
                    });

            V1Service service =
                    api.readNamespacedService(
                                    String.format("cosy-%s", server.getUuid()), config.namespace())
                            .execute();

            return getNodePorts(service);
        }
        catch (ApiException e) {
            throw new IllegalStateException("Failed to create pod or service", e);
        }
    }

    @Override
    public void stop(GameServerConfigurationEntity server) {
        findPod(server).ifPresent(this::deletePod);
        findService(server).ifPresent(this::deleteService);
    }

    @Override
    public String status(GameServerConfigurationEntity server) {
        return findPod(server)
                .map(
                        p ->
                                Optional.ofNullable(p.getStatus())
                                        .map(V1PodStatus::getPhase)
                                        .orElse("UNKNOWN"))
                .orElse("NOT_FOUND");
    }

    private Optional<V1Pod> findPod(GameServerConfigurationEntity server) {
        try {
            V1PodList pods =
                    api.listNamespacedPod(config.namespace())
                            .labelSelector(String.format("cosy-server=%s", server.getUuid()))
                            .execute();
            return pods.getItems().stream().findFirst();
        }
        catch (ApiException e) {
            throw new IllegalStateException("Failed to list pods", e);
        }
    }

    private Optional<V1Service> findService(GameServerConfigurationEntity server) {
        try {
            V1ServiceList services =
                    api.listNamespacedService(config.namespace())
                            .labelSelector(String.format("cosy-server=%s", server.getUuid()))
                            .execute();
            return services.getItems().stream().findFirst();
        }
        catch (ApiException e) {
            throw new IllegalStateException("Failed to list services", e);
        }
    }

    private void createPod(V1Pod pod) {
        try {
            api.createNamespacedPod(config.namespace(), pod).execute();
        }
        catch (ApiException e) {
            throw new IllegalStateException("Failed to create pod", e);
        }
    }

    private void deletePod(V1Pod pod) {
        V1ObjectMeta metadata = pod.getMetadata();
        if (metadata == null || metadata.getName() == null) {
            throw new IllegalStateException("Cannot delete pod: metadata or name is null");
        }

        try {
            api.deleteNamespacedPod(metadata.getName(), config.namespace()).execute();
        }
        catch (ApiException e) {
            throw new IllegalStateException("Failed to delete pod", e);
        }
    }

    private void deleteService(V1Service service) {
        V1ObjectMeta metadata = service.getMetadata();
        if (metadata == null || metadata.getName() == null) {
            throw new IllegalStateException("Cannot delete service: metadata or name is null");
        }

        try {
            api.deleteNamespacedService(metadata.getName(), config.namespace()).execute();
        }
        catch (ApiException e) {
            throw new IllegalStateException("Failed to delete service", e);
        }
    }

    private List<Integer> getNodePorts(V1Service service) {
        return Optional.ofNullable(service.getSpec())
                .map(V1ServiceSpec::getPorts)
                .orElse(List.of())
                .stream()
                .map(V1ServicePort::getNodePort)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private V1Pod buildPod(GameServerConfigurationEntity server) {
        return new V1Pod()
                .metadata(new V1ObjectMeta().name(podName(server)).labels(buildLabels(server)))
                .spec(
                        new V1PodSpec()
                                .containers(List.of(buildContainer(server)))
                                .restartPolicy("Never")
                                .overhead(null)
                                .runtimeClassName(null));
    }

    private V1Container buildContainer(GameServerConfigurationEntity server) {
        return new V1Container()
                .name("game-server")
                .image(buildImage(server))
                .imagePullPolicy("IfNotPresent")
                .command(server.getDockerExecutionCommand())
                .env(mapEnvironment(server.getEnvironmentVariables()))
                .ports(mapPorts(server.getPortMappings()));
    }

    private void createService(GameServerConfigurationEntity server) {
        if (server.getPortMappings() == null || server.getPortMappings().isEmpty()) {
            return;
        }

        List<V1ServicePort> servicePorts =
                server.getPortMappings().stream()
                        .map(
                                p ->
                                        new V1ServicePort()
                                                .name(
                                                        String.format(
                                                                "port-%d", p.getContainerPort()))
                                                .port(p.getContainerPort())
                                                .targetPort(new IntOrString(p.getContainerPort())))
                        .collect(Collectors.toList());

        V1Service service =
                new V1Service()
                        .metadata(
                                new V1ObjectMeta()
                                        .name(String.format("cosy-%s", server.getUuid()))
                                        .labels(buildLabels(server)))
                        .spec(
                                new V1ServiceSpec()
                                        .type("NodePort")
                                        .selector(Map.of("cosy-server", server.getUuid()))
                                        .ports(servicePorts));

        try {
            api.createNamespacedService(config.namespace(), service).execute();
        }
        catch (ApiException e) {
            throw new IllegalStateException("Failed to create service", e);
        }
    }

    private String podName(GameServerConfigurationEntity server) {
        return String.format("cosy-%s", server.getUuid());
    }

    private String buildImage(GameServerConfigurationEntity server) {
        String tag = server.getDockerImageTag();
        return tag == null || tag.isBlank()
                ? server.getDockerImageName()
                : String.format("%s:%s", server.getDockerImageName(), tag);
    }

    private Map<String, String> buildLabels(GameServerConfigurationEntity server) {
        Map<String, String> labels = new HashMap<>();
        labels.put("cosy-server", server.getUuid());
        if (config.labels() != null) {
            labels.putAll(config.labels());
        }
        return labels;
    }

    private List<V1EnvVar> mapEnvironment(List<EnvironmentVariableConfiguration> envs) {
        return envs == null
                ? List.of()
                : envs.stream()
                .map(e -> new V1EnvVar().name(e.getKey()).value(e.getValue()))
                .collect(Collectors.toList());
    }

    private List<V1ContainerPort> mapPorts(List<PortMapping> ports) {
        return ports == null
                ? List.of()
                : ports.stream()
                .map(p -> new V1ContainerPort().containerPort(p.getContainerPort()))
                .distinct()
                .collect(Collectors.toList());
    }

    private void waitForPodRunning(String podName) throws ApiException {
        final int maxRetries = 60; // e.g., 60 * 1 second = 1 minute
        final int delayMillis = 1000;

        for (int i = 0; i < maxRetries; i++) {
            V1Pod pod = api.readNamespacedPod(podName, config.namespace()).execute();
            if (pod.getStatus() != null && "Running".equals(pod.getStatus().getPhase())) {
                return; // pod is running
            }

            try {
                Thread.sleep(delayMillis);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for pod to start", e);
            }
        }

        throw new IllegalStateException(
                String.format("Pod %s did not reach 'Running' state in time", podName));
    }
}
