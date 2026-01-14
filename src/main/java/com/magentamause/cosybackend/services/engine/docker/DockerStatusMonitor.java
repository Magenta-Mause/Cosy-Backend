package com.magentamause.cosybackend.services.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Event;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.GameServerEntity.GameServerStatus;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.websockets.GameServerStatusPublisher;
import jakarta.annotation.PostConstruct;
import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DockerStatusMonitor implements Closeable {

    private final DockerClient dockerClient;
    private final GameServerRepository gameServerRepository;
    private final GameServerStatusPublisher statusPublisher;
    private ResultCallback<Event> eventCallback;

    @PostConstruct
    public void init() {
        log.info("Initializing DockerStatusMonitor");
        syncAllContainerStatuses();
        startEventListener();
    }

    private void syncAllContainerStatuses() {
        List<GameServerEntity> servers = gameServerRepository.findAll();
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();

        for (GameServerEntity server : servers) {
            String containerName = "/cosy-" + server.getUuid();
            Optional<Container> container =
                    containers.stream()
                            .filter(
                                    c ->
                                            Arrays.asList(
                                                            Optional.ofNullable(c.getNames())
                                                                    .orElse(new String[0]))
                                                    .contains(containerName))
                            .findFirst();

            GameServerStatus newStatus;
            if (container.isPresent()) {
                String state = container.get().getState();
                newStatus = mapDockerStateToGameServerStatus(state);
            } else {
                newStatus = GameServerStatus.STOPPED; // Or FAILED if it was supposed to be running?
            }

            // Do not overwrite PULLING_IMAGE with STOPPED if container is missing (it's expected)
            if (server.getStatus() == GameServerStatus.PULLING_IMAGE
                    && newStatus == GameServerStatus.STOPPED) {
                continue;
            }

            if (server.getStatus() != newStatus) {
                server.setStatus(newStatus);
                gameServerRepository.save(server);
                statusPublisher.publishStatus(server.getUuid(), newStatus);
                log.info("Updated status for server {} to {}", server.getServerName(), newStatus);
            }
        }
    }

    private void startEventListener() {
        eventCallback =
                new ResultCallback.Adapter<>() {
                    @Override
                    public void onNext(Event event) {
                        handleEvent(event);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        log.error("Error in Docker event listener", throwable);
                    }
                };

        dockerClient
                .eventsCmd()
                .withEventTypeFilter("container")
                .withEventFilter("create", "start", "die", "stop", "destroy", "pause", "unpause")
                .exec(eventCallback);
    }

    private void handleEvent(Event event) {
        try {
            if (event.getActor() == null || event.getActor().getAttributes() == null) {
                return;
            }

            String containerName = event.getActor().getAttributes().get("name");
            if (containerName == null || !containerName.startsWith("cosy-")) {
                return;
            }

            String uuid = containerName.substring(5); // Remove "cosy-" prefix
            Optional<GameServerEntity> serverOpt = gameServerRepository.findById(uuid);

            if (serverOpt.isPresent()) {
                GameServerEntity server = serverOpt.get();
                // Prefer getAction(), fallback to getStatus()
                String eventName =
                        event.getAction() != null ? event.getAction() : event.getStatus();

                GameServerStatus newStatus;

                if ("die".equals(eventName)) {
                    String exitCodeStr = event.getActor().getAttributes().get("exitCode");
                    int exitCode = 0;
                    try {
                        if (exitCodeStr != null) {
                            exitCode = Integer.parseInt(exitCodeStr);
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Could not parse exitCode from Docker event: {}", exitCodeStr);
                    }
                    // If the server is already STOPPED, we assume this die event is the result of a clean stop
                    // and ignore non-zero exit codes (like 137 or 143) to avoid setting it to FAILED
                    if (server.getStatus() == GameServerStatus.STOPPED) {
                        newStatus = GameServerStatus.STOPPED;
                    } else {
                        newStatus = exitCode != 0 ? GameServerStatus.FAILED : GameServerStatus.STOPPED;
                    }
                } else {
                    newStatus = mapEventToStatus(eventName);
                }

                if (newStatus != null && server.getStatus() != newStatus) {
                    server.setStatus(newStatus);
                    gameServerRepository.save(server);
                    statusPublisher.publishStatus(server.getUuid(), newStatus);
                    log.info(
                            "Event: {} -> Updated status for server {} to {}",
                            eventName,
                            server.getServerName(),
                            newStatus);
                }
            }
        } catch (Exception e) {
            log.error("Error handling Docker event: {}", event, e);
        }
    }

    private GameServerStatus mapDockerStateToGameServerStatus(String state) {
        if ("running".equalsIgnoreCase(state)) {
            return GameServerStatus.RUNNING;
        } else if ("paused".equalsIgnoreCase(state)) {
            // Mapping paused to STOPPED
            return GameServerStatus.STOPPED;
        } else if ("restarting".equalsIgnoreCase(state)) {
            return GameServerStatus.RUNNING;
        } else {
            return GameServerStatus.STOPPED;
        }
    }

    private GameServerStatus mapEventToStatus(String eventName) {
        if (eventName == null) {
            return null;
        }
        switch (eventName) {
            case "create":
                return GameServerStatus.STOPPED;
            case "start":
            case "unpause":
                return GameServerStatus.RUNNING;
            case "stop":
            case "destroy":
                return GameServerStatus.STOPPED;
            case "pause":
                return GameServerStatus.STOPPED;
            default:
                return null;
        }
    }

    @Override
    public void close() throws IOException {
        if (eventCallback != null) {
            try {
                eventCallback.close();
            } catch (Exception e) {
                throw new IOException("Failed to close Docker event listener", e);
            }
        }
    }
}
