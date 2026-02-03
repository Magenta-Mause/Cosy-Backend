package com.magentamause.cosybackend.services.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.services.engine.docker.util.DockerContainerNameResolver;
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
                        }

                        @Override
                        public void onComplete() {
                            log.debug("Log listener for container {} completed", containerName);
                            attachments.remove(serviceConfig.getUuid());
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
                            .withLogs(true)
                            .exec(callback);

            attachments.put(
                    serviceConfig.getUuid(), new ContainerAttachment(stdinWriter, attachCloseable));

        } catch (IOException e) {
            log.error("Failed to attach to container {}", containerName, e);
        }
    }

    public PipedOutputStream getStdinWriter(String uuid) {
        ContainerAttachment attachment = attachments.get(uuid);
        return attachment != null ? attachment.stdinWriter : null;
    }

    private record ContainerAttachment(PipedOutputStream stdinWriter, Closeable attachCloseable) {}
}
