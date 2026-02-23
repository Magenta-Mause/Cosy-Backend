package com.magentamause.cosybackend.services.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Event;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.services.core.gameserver.GameServerStatusUpdateEventType;
import com.magentamause.cosybackend.services.engine.docker.util.DockerContainerNameResolver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Service for handling Docker events and managing status listeners. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerEventHandler implements Closeable {

    private final DockerClient client;
    private final DockerContainerNameResolver containerNameResolver;

    private final List<BiConsumer<GameServerStatusUpdateEventType, String>> statusListeners =
            new CopyOnWriteArrayList<>();
    private final Map<String, Supplier<GameServerDto.GameServerStatus>> statusSuppliers =
            new ConcurrentHashMap<>();

    private ResultCallback<Event> eventCallback;

    @PostConstruct
    public void init() {
        startEventListener();
    }

    public void attachStatusListener(BiConsumer<GameServerStatusUpdateEventType, String> listener) {
        statusListeners.add(listener);
    }

    public void attachStatusSupplier(
            String gameServerUuid, Supplier<GameServerDto.GameServerStatus> statusSupplier) {
        statusSuppliers.put(gameServerUuid, statusSupplier);
    }

    public void removeStatusSupplier(String gameServerUuid) {
        statusSuppliers.remove(gameServerUuid);
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

        client.eventsCmd()
                .withEventTypeFilter("container")
                .withEventFilter("start", "die", "stop", "pause", "unpause")
                .exec(eventCallback);
    }

    private void handleEvent(Event event) {
        if (event.getActor() == null || event.getActor().getAttributes() == null) {
            return;
        }

        String containerName = event.getActor().getAttributes().get("name");
        String prefix = containerNameResolver.getPrefix();
        if (containerName == null || !containerName.startsWith(prefix)) {
            return;
        }

        String uuid = containerNameResolver.extractUuidFromContainerName(containerName);
        String eventName = event.getAction();

        log.debug("Handling Docker event: {} for server {}", eventName, uuid);

        if (eventName == null) {
            log.warn("Received Docker event with null action: {}", event);
            return;
        }

        switch (eventName) {
            case "start":
            case "unpause":
                notifyListeners(GameServerStatusUpdateEventType.STARTED, uuid);
                break;
            case "die":
                handleDieEvent(uuid);
                break;
            default:
                log.warn("Received docker event with unexpected event action: {}", eventName);
        }
    }

    private void handleDieEvent(String uuid) {
        if (!statusSuppliers.containsKey(uuid)) {
            log.warn("No status supplier for server with uuid: {} found", uuid);
        }

        if (statusSuppliers.containsKey(uuid)
                && statusSuppliers
                        .get(uuid)
                        .get()
                        .equals(GameServerDto.GameServerStatus.STOPPING)) {
            notifyListeners(GameServerStatusUpdateEventType.STOPPED, uuid);
        } else {
            notifyListeners(GameServerStatusUpdateEventType.FAILED, uuid);
        }
    }

    private void notifyListeners(GameServerStatusUpdateEventType eventType, String uuid) {
        statusListeners.forEach(listener -> listener.accept(eventType, uuid));
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        if (eventCallback != null) {
            try {
                eventCallback.close();
            } catch (Exception e) {
                log.warn("Failed to close Docker event listener", e);
            }
        }
    }
}
