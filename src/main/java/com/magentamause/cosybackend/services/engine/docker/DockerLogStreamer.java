package com.magentamause.cosybackend.services.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.services.engine.docker.util.DockerContainerNameResolver;
import jakarta.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Service for streaming logs from Docker containers. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerLogStreamer {

    private final DockerClient client;
    private final DockerContainerNameResolver containerNameResolver;

    private final Map<String, ContainerAttachment> attachments = new ConcurrentHashMap<>();

    public void attachLogListener(
            GameServerEntity serviceConfig, Consumer<GameServerLogMessageEntity> listener) {
        String containerName = containerNameResolver.containerName(serviceConfig);

        try {
            PipedInputStream stdinPipe = new PipedInputStream();
            PipedOutputStream stdinWriter = new PipedOutputStream(stdinPipe);

            ResultCallback.Adapter<Frame> callback =
                    new ResultCallback.Adapter<>() {
                        @Override
                        public void onNext(Frame frame) {
                            String message = new String(frame.getPayload(), StandardCharsets.UTF_8);
                            GameServerLogMessageEntity logMessage =
                                    GameServerLogMessageEntity.builder()
                                            .message(message)
                                            .level(
                                                    frame.getStreamType() == StreamType.STDERR
                                                            ? GameServerLogMessageEntity.LogLevel
                                                                    .ERROR
                                                            : GameServerLogMessageEntity.LogLevel
                                                                    .INFO)
                                            .timestamp(Instant.now())
                                            .gameServerUuid(serviceConfig.getUuid())
                                            .build();

                            listener.accept(logMessage);
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            listener.accept(
                                    GameServerLogMessageEntity.builder()
                                            .message(throwable.getMessage())
                                            .level(GameServerLogMessageEntity.LogLevel.ERROR)
                                            .gameServerUuid(serviceConfig.getUuid())
                                            .timestamp(Instant.now())
                                            .build());
                            detachLogListener(serviceConfig.getUuid());
                        }

                        @Override
                        public void onComplete() {
                            log.debug("Log listener for container {} completed", containerName);
                            detachLogListener(serviceConfig.getUuid());
                        }

                        @Override
                        public void close() throws IOException {
                            super.close();
                            log.debug("Closing log listener for container {}", containerName);
                        }
                    };

            Closeable attachCloseable =
                    client.attachContainerCmd(containerName)
                            .withStdIn(stdinPipe)
                            .withStdOut(true)
                            .withStdErr(true)
                            .withFollowStream(true)
                            .withLogs(false)
                            .exec(callback);

            attachments.put(
                    serviceConfig.getUuid(),
                    new ContainerAttachment(stdinPipe, stdinWriter, attachCloseable));

        } catch (IOException e) {
            log.error("Failed to attach to container {}", containerName, e);
        }
    }

    public PipedOutputStream getStdinWriter(String uuid) {
        ContainerAttachment attachment = attachments.get(uuid);
        return attachment != null ? attachment.stdinWriter : null;
    }

    public void detachLogListener(String uuid) {
        ContainerAttachment attachment = attachments.remove(uuid);
        if (attachment != null) {
            closeAttachment(attachment);
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("Closing all active log attachments on shutdown");
        attachments.values().forEach(this::closeAttachment);
        attachments.clear();
    }

    private void closeAttachment(ContainerAttachment attachment) {
        closeResource(attachment.stdinWriter, "PipedOutputStream");
        closeResource(attachment.stdinPipe, "PipedInputStream");
        closeResource(attachment.attachCloseable, "Docker attachment");
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

    private record ContainerAttachment(
            PipedInputStream stdinPipe, PipedOutputStream stdinWriter, Closeable attachCloseable) {}
}
