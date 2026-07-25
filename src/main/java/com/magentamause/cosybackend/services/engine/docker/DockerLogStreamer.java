package com.magentamause.cosybackend.services.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.magentamause.cosybackend.configs.EngineConfiguration;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.services.engine.docker.util.DockerContainerNameResolver;
import com.magentamause.cosybackend.services.engine.docker.util.ReconnectBackoff;
import jakarta.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Streams logs from Docker containers and keeps the stdin pipe of an attachment available for
 * command sending.
 *
 * <p>Attachments are self-healing: as long as a listener is registered for a server and its
 * container is still running, a stream that ends — with an error or cleanly — is re-attached with a
 * capped exponential backoff. A deliberate {@link #detachLogListener(String)} removes the
 * registration first, so it always wins over a pending re-attach.
 */
@Slf4j
@Component
public class DockerLogStreamer {

    private static final String RUNNING_STATE = "running";

    /**
     * An attachment that survived at least this long is considered to have worked, so the backoff
     * restarts from the beginning after it drops.
     */
    private static final Duration STABLE_ATTACHMENT_THRESHOLD = Duration.ofMinutes(1);

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final DockerClient client;
    private final DockerContainerNameResolver containerNameResolver;
    private final DockerContainerFinder containerFinder;
    private final ReconnectBackoff backoff;

    private final Map<String, ContainerAttachment> attachments = new ConcurrentHashMap<>();

    /** The listeners that should be receiving logs — the intent, independent of the live stream. */
    private final Map<String, Consumer<GameServerLogMessageEntity>> listeners =
            new ConcurrentHashMap<>();

    private final Map<String, ScheduledFuture<?>> pendingReattachments = new ConcurrentHashMap<>();

    /** Dedicated thread so re-attaching never runs on — and never blocks — a docker callback. */
    private final ScheduledExecutorService reattachExecutor =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "docker-log-reattach");
                        thread.setDaemon(true);
                        return thread;
                    });

    private volatile boolean closing = false;

    @Autowired
    public DockerLogStreamer(
            @Qualifier(EngineConfiguration.STREAMING_DOCKER_CLIENT) DockerClient client,
            DockerContainerNameResolver containerNameResolver,
            DockerContainerFinder containerFinder) {
        this(client, containerNameResolver, containerFinder, ReconnectBackoff.defaultBackoff());
    }

    DockerLogStreamer(
            DockerClient client,
            DockerContainerNameResolver containerNameResolver,
            DockerContainerFinder containerFinder,
            ReconnectBackoff backoff) {
        this.client = client;
        this.containerNameResolver = containerNameResolver;
        this.containerFinder = containerFinder;
        this.backoff = backoff;
    }

    public void attachLogListener(
            GameServerEntity serviceConfig, Consumer<GameServerLogMessageEntity> listener) {
        listeners.put(serviceConfig.getUuid(), listener);
        openAttachment(serviceConfig, 0);
    }

    /** Whether logs of the given server are currently being streamed or scheduled to be. */
    public boolean isLogListenerAttached(String uuid) {
        return listeners.containsKey(uuid);
    }

    public PipedOutputStream getStdinWriter(String uuid) {
        ContainerAttachment attachment = attachments.get(uuid);
        return attachment != null ? attachment.stdinWriter() : null;
    }

    /**
     * Cleans up a broken attachment so it can be recreated. Called when stdin write fails (e.g.
     * after system sleep). The listener registration is kept, so the caller can re-attach.
     */
    public void cleanupAttachment(String uuid) {
        log.info("Cleaning up attachment for server {}", uuid);
        closeCurrentAttachment(uuid);
    }

    /** Stops streaming logs for the given server and cancels any pending re-attach. */
    public void detachLogListener(String uuid) {
        listeners.remove(uuid);
        cancelPendingReattachment(uuid);
        closeCurrentAttachment(uuid);
    }

    @PreDestroy
    public void cleanup() {
        log.info("Closing all active log attachments on shutdown");
        closing = true;
        reattachExecutor.shutdownNow();
        try {
            if (!reattachExecutor.awaitTermination(
                    SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("Docker log re-attach thread did not terminate within the timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        listeners.clear();
        pendingReattachments.clear();
        attachments.keySet().forEach(this::closeCurrentAttachment);
        attachments.clear();
    }

    private void openAttachment(GameServerEntity serviceConfig, int precedingAttempts) {
        String uuid = serviceConfig.getUuid();
        if (closing || !listeners.containsKey(uuid)) {
            return;
        }

        // Replace any previous stream for this server without letting its termination schedule a
        // competing re-attach.
        closeCurrentAttachment(uuid);

        String containerName = containerNameResolver.containerName(serviceConfig);
        try {
            PipedInputStream stdinPipe = new PipedInputStream();
            PipedOutputStream stdinWriter = new PipedOutputStream(stdinPipe);
            AtomicBoolean closedDeliberately = new AtomicBoolean(false);

            ResultCallback.Adapter<Frame> callback =
                    new LogStreamCallback(
                            serviceConfig, containerName, closedDeliberately, precedingAttempts);

            Closeable attachCloseable =
                    client.attachContainerCmd(containerName)
                            .withStdIn(stdinPipe)
                            .withStdOut(true)
                            .withStdErr(true)
                            .withFollowStream(true)
                            .withLogs(false)
                            .exec(callback);

            attachments.put(
                    uuid,
                    new ContainerAttachment(
                            stdinPipe, stdinWriter, attachCloseable, closedDeliberately));
        } catch (IOException | RuntimeException e) {
            log.error("Failed to attach to container {}", containerName, e);
            scheduleReattachment(serviceConfig, precedingAttempts + 1);
        }
    }

    private void scheduleReattachment(GameServerEntity serviceConfig, int attempt) {
        String uuid = serviceConfig.getUuid();
        if (closing || !listeners.containsKey(uuid)) {
            return;
        }

        Duration delay = backoff.delayForAttempt(attempt - 1);
        log.warn(
                "Re-attaching log stream of server {} in {} ms (attempt {})",
                uuid,
                delay.toMillis(),
                attempt);
        try {
            ScheduledFuture<?> scheduled =
                    reattachExecutor.schedule(
                            () -> reattach(serviceConfig, attempt),
                            delay.toMillis(),
                            TimeUnit.MILLISECONDS);
            pendingReattachments.put(uuid, scheduled);
        } catch (RejectedExecutionException e) {
            // Only expected during shutdown, when the executor has already been stopped.
            log.debug("Log stream re-attach rejected, streamer is shutting down", e);
        }
    }

    private void reattach(GameServerEntity serviceConfig, int attempt) {
        String uuid = serviceConfig.getUuid();
        pendingReattachments.remove(uuid);
        if (closing || !listeners.containsKey(uuid)) {
            return;
        }

        if (!isContainerRunning(serviceConfig)) {
            log.info(
                    "Container of server {} is no longer running, giving up on re-attaching its log"
                            + " stream",
                    uuid);
            detachLogListener(uuid);
            return;
        }

        openAttachment(serviceConfig, attempt);
        if (attachments.containsKey(uuid)) {
            log.info(
                    "Log stream of server {} re-attached after {} failed attempt(s)",
                    uuid,
                    attempt);
        }
    }

    private boolean isContainerRunning(GameServerEntity serviceConfig) {
        try {
            Optional<Container> container = containerFinder.findContainer(serviceConfig);
            return container.filter(c -> RUNNING_STATE.equalsIgnoreCase(c.getState())).isPresent();
        } catch (RuntimeException e) {
            // The daemon may be unreachable; assume the container is still there and retry.
            log.warn(
                    "Could not determine container state of server {} while re-attaching",
                    serviceConfig.getUuid(),
                    e);
            return true;
        }
    }

    private void cancelPendingReattachment(String uuid) {
        ScheduledFuture<?> pending = pendingReattachments.remove(uuid);
        if (pending != null) {
            pending.cancel(false);
        }
    }

    private void closeCurrentAttachment(String uuid) {
        ContainerAttachment attachment = attachments.remove(uuid);
        if (attachment == null) {
            return;
        }
        attachment.closedDeliberately().set(true);
        closeResource(attachment.stdinWriter(), "PipedOutputStream");
        closeResource(attachment.stdinPipe(), "PipedInputStream");
        closeResource(attachment.attachCloseable(), "Docker attachment");
    }

    private void closeResource(Closeable resource, String resourceName) {
        if (resource != null) {
            try {
                resource.close();
                log.debug("Successfully closed {}", resourceName);
            } catch (IOException e) {
                log.warn("Failed to close {}: {}", resourceName, e.getMessage());
            }
        }
    }

    /**
     * Callback for a single container attachment. Each instance terminates at most once, so a
     * stream that reports both an error and a completion only triggers a single re-attach.
     */
    private final class LogStreamCallback extends ResultCallback.Adapter<Frame> {

        private final GameServerEntity serviceConfig;
        private final String containerName;
        private final AtomicBoolean closedDeliberately;
        private final int precedingAttempts;
        private final Instant startedAt = Instant.now();
        private final AtomicBoolean terminated = new AtomicBoolean(false);

        private LogStreamCallback(
                GameServerEntity serviceConfig,
                String containerName,
                AtomicBoolean closedDeliberately,
                int precedingAttempts) {
            this.serviceConfig = serviceConfig;
            this.containerName = containerName;
            this.closedDeliberately = closedDeliberately;
            this.precedingAttempts = precedingAttempts;
        }

        @Override
        public void onNext(Frame frame) {
            Consumer<GameServerLogMessageEntity> listener = listeners.get(serviceConfig.getUuid());
            if (listener == null) {
                return;
            }
            String message = new String(frame.getPayload(), StandardCharsets.UTF_8);
            listener.accept(
                    GameServerLogMessageEntity.builder()
                            .message(message)
                            .level(
                                    GameServerLogMessageEntity.LogLevel.ofStreamType(
                                            frame.getStreamType()))
                            .timestamp(Instant.now())
                            .gameServerUuid(serviceConfig.getUuid())
                            .build());
        }

        @Override
        public void onError(Throwable throwable) {
            if (!terminated.compareAndSet(false, true)) {
                return;
            }
            closeQuietly();
            Consumer<GameServerLogMessageEntity> listener = listeners.get(serviceConfig.getUuid());
            if (listener != null) {
                listener.accept(
                        GameServerLogMessageEntity.builder()
                                .message(throwable.getMessage())
                                .level(GameServerLogMessageEntity.LogLevel.ERROR)
                                .gameServerUuid(serviceConfig.getUuid())
                                .timestamp(Instant.now())
                                .build());
            }
            log.warn("Log stream of container {} failed", containerName, throwable);
            scheduleReattachmentIfStillWanted();
        }

        @Override
        public void onComplete() {
            if (!terminated.compareAndSet(false, true)) {
                return;
            }
            closeQuietly();
            log.debug("Log listener for container {} completed", containerName);
            scheduleReattachmentIfStillWanted();
        }

        /**
         * Releases the underlying HTTP connection. {@link ResultCallback.Adapter} only does this
         * from its own {@code onError}/{@code onComplete}, which this callback overrides.
         */
        private void closeQuietly() {
            try {
                close();
            } catch (IOException e) {
                log.debug("Failed to close the terminated log stream of {}", containerName, e);
            }
        }

        private void scheduleReattachmentIfStillWanted() {
            if (closedDeliberately.get() || closing) {
                return;
            }
            boolean wasStable =
                    Duration.between(startedAt, Instant.now())
                                    .compareTo(STABLE_ATTACHMENT_THRESHOLD)
                            >= 0;
            scheduleReattachment(serviceConfig, wasStable ? 1 : precedingAttempts + 1);
        }
    }

    private record ContainerAttachment(
            PipedInputStream stdinPipe,
            PipedOutputStream stdinWriter,
            Closeable attachCloseable,
            AtomicBoolean closedDeliberately) {}
}
