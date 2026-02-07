package com.magentamause.cosybackend.services.engine.docker;

import com.github.dockerjava.api.model.Container;
import com.magentamause.cosybackend.entities.GameServerEntity;
import java.io.IOException;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Service for sending commands to Docker containers via stdin. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerCommandSender {

    private static final int MAX_RETRIES = 2;
    private static final long REATTACH_DELAY_MS = 500;
    private static final String RUNNING_STATE = "running";

    private final DockerContainerFinder containerFinder;
    private final DockerLogStreamer logStreamer;

    public void sendCommand(GameServerEntity serverConfig, String command) throws IOException {
        validateContainerRunning(serverConfig);
        sendCommandWithRetry(serverConfig, command);
    }

    private void validateContainerRunning(GameServerEntity serverConfig) throws IOException {
        Container container =
                containerFinder
                        .findContainer(serverConfig)
                        .orElseThrow(
                                () ->
                                        new IOException(
                                                "Container not found for server: "
                                                        + serverConfig.getServerName()));

        if (!RUNNING_STATE.equalsIgnoreCase(container.getState())) {
            throw new IOException(
                    "Container is not running for server: " + serverConfig.getServerName());
        }
    }

    private void sendCommandWithRetry(GameServerEntity serverConfig, String command)
            throws IOException {
        IOException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                sendCommandInternal(serverConfig, command, attempt);
                return;
            } catch (IOException e) {
                lastException = e;
                log.warn(
                        "Attempt {} failed to send command to container {}: {}",
                        attempt,
                        serverConfig.getServerName(),
                        e.getMessage());

                if (attempt < MAX_RETRIES) {
                    reestablishConnection(serverConfig);
                }
            }
        }

        throw new IOException(
                "Failed to send command after " + MAX_RETRIES + " attempts", lastException);
    }

    private void reestablishConnection(GameServerEntity serverConfig) {
        log.info("Cleaning up broken stdin connection, will be recreated on retry...");
        logStreamer.cleanupAttachment(serverConfig.getUuid());

        try {
            logStreamer.attachLogListener(serverConfig, msg -> {});
            Thread.sleep(REATTACH_DELAY_MS);
        } catch (Exception e) {
            log.warn("Failed to re-establish connection: {}", e.getMessage());
        }
    }

    private void sendCommandInternal(GameServerEntity serverConfig, String command, int attempt)
            throws IOException {
        PipedOutputStream stdinWriter = logStreamer.getStdinWriter(serverConfig.getUuid());

        if (stdinWriter == null) {
            throw new IOException(
                    "No active stdin connection found for server: "
                            + serverConfig.getServerName()
                            + ". Ensure logs are being streamed or try again.");
        }

        try {
            log.info("Sending command to container via stdin (attempt {}): '{}'", attempt, command);
            stdinWriter.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            stdinWriter.flush();
            log.info(
                    "Command sent successfully to container {}: '{}'",
                    serverConfig.getServerName(),
                    command);
        } catch (IOException e) {
            log.warn("IOException while writing to stdin stream: {}", e.getMessage());
            // Write failed - connection is likely broken, throw to trigger retry
            throw new IOException("Failed to write command to stdin", e);
        }
    }
}
