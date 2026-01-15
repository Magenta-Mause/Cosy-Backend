package com.magentamause.cosybackend.services.engine.kubernetes;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerStatusDto;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.Metric;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.entities.utility.EnvironmentVariableConfiguration;
import com.magentamause.cosybackend.entities.utility.PortMapping;
import com.magentamause.cosybackend.exceptions.CreateGameInstanceException;
import com.magentamause.cosybackend.exceptions.ServerAlreadyStoppedException;
import com.magentamause.cosybackend.services.engine.EngineManager;
import com.magentamause.cosybackend.services.engine.config.EngineProperties.Kubernetes;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.PodMetrics;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import okhttp3.Call;
import okhttp3.Response;

@AllArgsConstructor
public class KubernetesEngineManager implements EngineManager {

    private final Kubernetes config;
    private final CoreV1Api api;

    @Override
    public List<Integer> start(GameServerEntity server) {
        V1Pod pod = getOrCreatePod(server);
        V1Service service = getOrCreateService(server);
        return getNodePorts(service);
    }

    @Override
    public void stop(GameServerEntity server) {
        boolean podExists = findPod(server).isPresent();
        boolean serviceExists = findService(server).isPresent();

        if (!podExists && !serviceExists) {
            throw new ServerAlreadyStoppedException(server.getServerName());
        }

        findPod(server).ifPresent(this::deletePod);
        findService(server).ifPresent(this::deleteService);
    }

    @Override
    public GameServerStatusDto status(GameServerEntity server) {
        Optional<V1Pod> pod = findPod(server);
        if (pod.isEmpty()) {
            return GameServerStatusDto.builder()
                    .status(GameServerStatusDto.GameServerStatus.NotFound)
                    .build();
        }

        String phase =
                Optional.ofNullable(pod.get().getStatus())
                        .map(V1PodStatus::getPhase)
                        .orElse("UNKNOWN");

        return GameServerStatusDto.builder()
                .status(GameServerStatusDto.GameServerStatus.Found)
                .phase(phase)
                .build();
    }

    @Override
    public Metric collectMetric(GameServerEntity serverConfig) throws InterruptedException {
        return null;
    }


    @Override
    public void attachLogListener(
            GameServerEntity server, Consumer<GameServerLogMessageEntity> listener) {
        V1Pod pod =
                findPod(server)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Cannot attach logs: pod not found"));

        String podName = pod.getMetadata().getName();
        String namespace = config.namespace();
        String containerName = "game-server";

        Thread logThread =
                new Thread(
                        () -> {
                            try {
                                // Build streaming call
                                Call call =
                                        api.readNamespacedPodLog(podName, namespace)
                                                .container(containerName)
                                                .follow(true)
                                                .timestamps(false)
                                                .tailLines(null)
                                                .buildCall(null);

                                // Execute and stream
                                try (Response response = call.execute();
                                        InputStream stream = response.body().byteStream();
                                        Scanner scanner =
                                                new Scanner(stream, StandardCharsets.UTF_8)) {

                                    while (!Thread.currentThread().isInterrupted()
                                            && scanner.hasNextLine()) {

                                        String line = scanner.nextLine();

                                        listener.accept(
                                                GameServerLogMessageEntity.builder()
                                                        .message(line)
                                                        .level(
                                                                GameServerLogMessageEntity.LogLevel
                                                                        .INFO)
                                                        .timestamp(Instant.now())
                                                        .build());
                                    }
                                }
                            } catch (Exception e) {
                                listener.accept(
                                        GameServerLogMessageEntity.builder()
                                                .message(e.getMessage())
                                                .level(GameServerLogMessageEntity.LogLevel.ERROR)
                                                .timestamp(Instant.now())
                                                .build());
                            }
                        },
                        "k8s-log-stream-" + server.getUuid());

        logThread.setDaemon(true);
        logThread.start();
    }

    private Optional<V1Pod> findPod(GameServerEntity server) {
        try {
            V1PodList pods =
                    api.listNamespacedPod(config.namespace())
                            .labelSelector(String.format("cosy-server=%s", server.getUuid()))
                            .execute();
            return pods.getItems().stream().findFirst();
        } catch (ApiException e) {
            throw new IllegalStateException("Failed to list pods", e);
        }
    }

    private Optional<V1Service> findService(GameServerEntity server) {
        try {
            V1ServiceList services =
                    api.listNamespacedService(config.namespace())
                            .labelSelector(String.format("cosy-server=%s", server.getUuid()))
                            .execute();
            return services.getItems().stream().findFirst();
        } catch (ApiException e) {
            throw new IllegalStateException("Failed to list services", e);
        }
    }

    private void createPod(V1Pod pod) {
        try {
            api.createNamespacedPod(config.namespace(), pod).execute();
        } catch (ApiException e) {
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
        } catch (ApiException e) {
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
        } catch (ApiException e) {
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

    private V1Pod buildPod(GameServerEntity server) {
        return new V1Pod()
                .metadata(new V1ObjectMeta().name(podName(server)).labels(buildLabels(server)))
                .spec(
                        new V1PodSpec()
                                .containers(List.of(buildContainer(server)))
                                .restartPolicy("Never")
                                .overhead(null)
                                .runtimeClassName(null));
    }

    private V1Container buildContainer(GameServerEntity server) {
        return new V1Container()
                .name("game-server")
                .image(buildImage(server))
                .imagePullPolicy("IfNotPresent")
                .command(server.getDockerExecutionCommand())
                .env(mapEnvironment(server.getEnvironmentVariables()))
                .ports(mapPorts(server.getPortMappings()));
    }

    private void createService(GameServerEntity server) {
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
        } catch (ApiException e) {
            throw new IllegalStateException("Failed to create service", e);
        }
    }

    private V1Pod getOrCreatePod(GameServerEntity server) {
        return findPod(server)
                .orElseGet(
                        () -> {
                            V1Pod pod = buildPod(server);
                            createPod(pod);
                            try {
                                waitForPodRunning(podName(server));
                            } catch (ApiException e) {
                                throw new CreateGameInstanceException(
                                        "Failed waiting for pod to start", e);
                            }
                            return pod;
                        });
    }

    private V1Service getOrCreateService(GameServerEntity server) {
        return findService(server)
                .orElseGet(
                        () -> {
                            createService(server);
                            return findService(server)
                                    .orElseThrow(
                                            () ->
                                                    new CreateGameInstanceException(
                                                            "Service creation failed"));
                        });
    }

    private String podName(GameServerEntity server) {
        return String.format("cosy-%s", server.getUuid());
    }

    private String buildImage(GameServerEntity server) {
        String tag = server.getDockerImageTag();
        return tag == null || tag.isBlank()
                ? server.getDockerImageName()
                : String.format("%s:%s", server.getDockerImageName(), tag);
    }

    private Map<String, String> buildLabels(GameServerEntity server) {
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
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CreateGameInstanceException(
                        "Interrupted while waiting for pod to start", e);
            }
        }

        throw new CreateGameInstanceException(
                String.format("Pod %s did not reach 'Running' state in time", podName));
    }
}
