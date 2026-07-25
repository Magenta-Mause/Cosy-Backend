package com.magentamause.cosybackend.services.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Event;
import com.magentamause.cosybackend.configs.EngineConfiguration;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.services.core.gameserver.GameServerStatusUpdateEventType;
import com.magentamause.cosybackend.services.engine.docker.util.DockerContainerNameResolver;
import com.magentamause.cosybackend.services.engine.docker.util.ReconnectBackoff;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Subscribes to the Docker {@code /events} stream and translates container events into game server
 * status updates.
 *
 * <p>The subscription is self-healing: whenever the stream ends — with an error or cleanly — it is
 * re-established with a capped exponential backoff. Events that happened while the stream was down
 * are unrecoverable, so every successful (re)connect also notifies the registered connection
 * listeners, which re-reconcile persisted status against the real container state.
 */
@Slf4j
@Component
public class DockerEventHandler implements Closeable {

    private static final String CONTAINER_EVENT_TYPE = "container";

    /**
     * {@code stop} is deliberately not subscribed to: Docker emits it when the stop command is
     * issued, and always follows it with {@code die} once the container has actually exited. Only
     * {@code die} carries the exit code, so it is the single source of truth for the stop
     * transition.
     */
    private static final String[] SUBSCRIBED_EVENT_ACTIONS = {"start", "die", "pause", "unpause"};

    private static final String CONTAINER_NAME_ATTRIBUTE = "name";
    private static final String EXIT_CODE_ATTRIBUTE = "exitCode";

    /** A container that exits with 0 shut down cleanly. */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 128 + SIGTERM(15): the container was asked to terminate and did not install a handler. This
     * is how {@code docker stop} normally ends a container, so it is a graceful stop rather than a
     * crash.
     */
    private static final int EXIT_CODE_SIGTERM = 143;

    /**
     * A subscription that survived at least this long is considered to have worked, so the backoff
     * restarts from the beginning after it drops. Without this, a stream that is quiet for hours
     * and then ends would immediately retry at the maximum delay.
     */
    private static final Duration STABLE_CONNECTION_THRESHOLD = Duration.ofMinutes(1);

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final DockerClient client;
    private final DockerContainerNameResolver containerNameResolver;
    private final ReconnectBackoff backoff;

    private final List<BiConsumer<GameServerStatusUpdateEventType, String>> statusListeners =
            new CopyOnWriteArrayList<>();
    private final List<Runnable> connectionListeners = new CopyOnWriteArrayList<>();
    private final Map<String, Supplier<GameServerDto.GameServerStatus>> statusSuppliers =
            new ConcurrentHashMap<>();

    /**
     * Single dedicated thread so that reconnecting (and the reconciliation it triggers) never runs
     * on — and never blocks — a docker-java callback thread.
     */
    private final ScheduledExecutorService reconnectExecutor =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "docker-event-reconnect");
                        thread.setDaemon(true);
                        return thread;
                    });

    private volatile ResultCallback<Event> eventCallback;
    private volatile boolean closing = false;

    @Autowired
    public DockerEventHandler(
            @Qualifier(EngineConfiguration.STREAMING_DOCKER_CLIENT) DockerClient client,
            DockerContainerNameResolver containerNameResolver) {
        this(client, containerNameResolver, ReconnectBackoff.defaultBackoff());
    }

    DockerEventHandler(
            DockerClient client,
            DockerContainerNameResolver containerNameResolver,
            ReconnectBackoff backoff) {
        this.client = client;
        this.containerNameResolver = containerNameResolver;
        this.backoff = backoff;
    }

    @PostConstruct
    public void init() {
        connect(0);
    }

    public void attachStatusListener(BiConsumer<GameServerStatusUpdateEventType, String> listener) {
        statusListeners.add(listener);
    }

    /**
     * Registers a callback invoked on the reconnect thread after the event subscription has been
     * (re)established. Listeners are expected to re-reconcile their state, because any event
     * emitted while the stream was down is lost.
     */
    public void attachConnectionListener(Runnable listener) {
        connectionListeners.add(listener);
    }

    public void attachStatusSupplier(
            String gameServerUuid, Supplier<GameServerDto.GameServerStatus> statusSupplier) {
        statusSuppliers.put(gameServerUuid, statusSupplier);
    }

    public void removeStatusSupplier(String gameServerUuid) {
        statusSuppliers.remove(gameServerUuid);
    }

    private void connect(int attempt) {
        if (closing) {
            return;
        }

        try {
            EventStreamCallback callback = new EventStreamCallback(attempt);
            client.eventsCmd()
                    .withEventTypeFilter(CONTAINER_EVENT_TYPE)
                    .withEventFilter(SUBSCRIBED_EVENT_ACTIONS)
                    .exec(callback);
            eventCallback = callback;
        } catch (RuntimeException e) {
            log.warn("Failed to subscribe to the Docker event stream (attempt {})", attempt + 1, e);
            scheduleReconnect(attempt + 1);
            return;
        }

        if (attempt > 0) {
            log.info("Docker event stream re-established after {} failed attempt(s)", attempt);
        } else {
            log.info("Subscribed to the Docker event stream");
        }
        notifyConnectionListeners();
    }

    private void notifyConnectionListeners() {
        for (Runnable listener : connectionListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                log.error("Docker event stream connection listener failed", e);
            }
        }
    }

    private void scheduleReconnect(int attempt) {
        if (closing) {
            return;
        }
        Duration delay = backoff.delayForAttempt(attempt - 1);
        log.warn(
                "Reconnecting to the Docker event stream in {} ms (attempt {})",
                delay.toMillis(),
                attempt);
        try {
            reconnectExecutor.schedule(
                    () -> connect(attempt), delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // Only expected during shutdown, when the executor has already been stopped.
            log.debug("Docker event stream reconnect rejected, handler is shutting down", e);
        }
    }

    private void handleEvent(Event event) {
        if (event.getActor() == null || event.getActor().getAttributes() == null) {
            return;
        }

        Map<String, String> attributes = event.getActor().getAttributes();
        String containerName = attributes.get(CONTAINER_NAME_ATTRIBUTE);
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
                handleDieEvent(uuid, parseExitCode(attributes));
                break;
            default:
                log.warn("Received docker event with unexpected event action: {}", eventName);
        }
    }

    private Integer parseExitCode(Map<String, String> attributes) {
        String rawExitCode = attributes.get(EXIT_CODE_ATTRIBUTE);
        if (rawExitCode == null) {
            return null;
        }
        try {
            return Integer.valueOf(rawExitCode);
        } catch (NumberFormatException e) {
            log.warn("Received Docker die event with unparsable exit code '{}'", rawExitCode);
            return null;
        }
    }

    /**
     * Maps a {@code die} event onto a stopped-or-failed transition.
     *
     * <p>The persisted status alone is not sufficient: if the {@code STOPPING} transition was
     * missed (for example while the event stream was down) a perfectly normal shutdown would be
     * reported as {@code FAILED}, which is terminal for clients. The exit code carried by the event
     * is therefore consulted as well, and only a genuinely unexpected exit is treated as a failure.
     */
    private void handleDieEvent(String uuid, Integer exitCode) {
        Supplier<GameServerDto.GameServerStatus> statusSupplier = statusSuppliers.get(uuid);
        if (statusSupplier == null) {
            log.warn("No status supplier for server with uuid: {} found", uuid);
        } else if (statusSupplier.get() == GameServerDto.GameServerStatus.STOPPING) {
            notifyListeners(GameServerStatusUpdateEventType.STOPPED, uuid);
            return;
        }

        if (exitCode != null && (exitCode == EXIT_CODE_SUCCESS || exitCode == EXIT_CODE_SIGTERM)) {
            log.info(
                    "Container of server {} exited gracefully with code {} without a preceding stop"
                            + " request; treating it as stopped rather than failed",
                    uuid,
                    exitCode);
            notifyListeners(GameServerStatusUpdateEventType.STOPPED, uuid);
            return;
        }

        log.warn("Container of server {} died unexpectedly with exit code {}", uuid, exitCode);
        notifyListeners(GameServerStatusUpdateEventType.FAILED, uuid);
    }

    private void notifyListeners(GameServerStatusUpdateEventType eventType, String uuid) {
        statusListeners.forEach(listener -> listener.accept(eventType, uuid));
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        // Set first so in-flight callbacks and scheduled tasks become no-ops instead of racing the
        // shutdown with another reconnect.
        closing = true;
        reconnectExecutor.shutdownNow();
        try {
            if (!reconnectExecutor.awaitTermination(
                    SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("Docker event reconnect thread did not terminate within the timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ResultCallback<Event> callback = eventCallback;
        if (callback != null) {
            try {
                callback.close();
            } catch (Exception e) {
                log.warn("Failed to close Docker event listener", e);
            }
        }
    }

    /**
     * Callback for a single event stream subscription. Each instance terminates at most once, so a
     * stream that reports both an error and a completion only triggers a single reconnect.
     */
    private final class EventStreamCallback extends ResultCallback.Adapter<Event> {

        private final Instant startedAt = Instant.now();
        private final AtomicBoolean terminated = new AtomicBoolean(false);

        /** Number of consecutive failed attempts that preceded this subscription. */
        private final int precedingAttempts;

        private EventStreamCallback(int precedingAttempts) {
            this.precedingAttempts = precedingAttempts;
        }

        @Override
        public void onNext(Event event) {
            handleEvent(event);
        }

        @Override
        public void onError(Throwable throwable) {
            if (!terminated.compareAndSet(false, true)) {
                return;
            }
            closeQuietly();
            if (closing) {
                log.debug("Docker event stream failed during shutdown", throwable);
                return;
            }
            log.warn("Docker event stream failed, scheduling reconnect", throwable);
            scheduleReconnect(nextAttempt());
        }

        @Override
        public void onComplete() {
            if (!terminated.compareAndSet(false, true)) {
                return;
            }
            closeQuietly();
            if (closing) {
                log.debug("Docker event stream completed during shutdown");
                return;
            }
            log.warn("Docker event stream ended unexpectedly, scheduling reconnect");
            scheduleReconnect(nextAttempt());
        }

        /**
         * Releases the underlying HTTP connection. {@link ResultCallback.Adapter} only does this
         * from its own {@code onError}/{@code onComplete}, which this callback overrides.
         */
        private void closeQuietly() {
            try {
                close();
            } catch (IOException e) {
                log.debug("Failed to close the terminated Docker event stream", e);
            }
        }

        private int nextAttempt() {
            boolean wasStable =
                    Duration.between(startedAt, Instant.now())
                                    .compareTo(STABLE_CONNECTION_THRESHOLD)
                            >= 0;
            return wasStable ? 1 : precedingAttempts + 1;
        }
    }
}
