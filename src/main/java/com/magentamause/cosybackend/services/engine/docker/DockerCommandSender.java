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

    private final DockerContainerFinder containerFinder;
    private final DockerLogStreamer logStreamer;

    public void sendCommand(GameServerEntity serverConfig, String command) throws IOException {
        Container container =
                containerFinder
                        .findContainer(serverConfig)
                        .orElseThrow(
                                () ->
                                        new IOException(
                                                "Container not found for server: "
                                                        + serverConfig.getServerName()));

        if (!"running".equalsIgnoreCase(container.getState())) {
            throw new IOException(
                    "Container is not running for server: " + serverConfig.getServerName());
        }

        PipedOutputStream stdinWriter = logStreamer.getStdinWriter(serverConfig.getUuid());
        if (stdinWriter == null) {
            throw new IOException(
                    "No active log attachment found for server: "
                            + serverConfig.getServerName()
                            + ". Ensure logs are being streamed before sending commands.");
        }

        try {
            stdinWriter.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            stdinWriter.flush();
        } catch (IOException e) {
            throw new IOException("Failed to send command to container", e);
        }
    }
}
