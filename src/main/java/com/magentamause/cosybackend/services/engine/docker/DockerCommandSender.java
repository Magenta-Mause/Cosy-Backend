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

        // Try sending command with automatic retry on connection failure
        int maxRetries = 2;
        IOException lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                sendCommandInternal(serverConfig, command, attempt);
                return; // Success!
            } catch (IOException e) {
                lastException = e;
                log.warn("Attempt {} failed to send command to container {}: {}", 
                        attempt, serverConfig.getServerName(), e.getMessage());
                
                if (attempt < maxRetries) {
                    // Clean up broken connection and let logStreamer recreate on next log request
                    log.info("Cleaning up broken stdin connection, will be recreated on retry...");
                    logStreamer.cleanupAttachment(serverConfig.getUuid());
                    
                    // Re-attach by requesting logs again (which creates the stdin connection)
                    try {
                        logStreamer.attachLogListener(serverConfig, msg -> {
                            // Dummy listener to trigger re-attachment
                        });
                        Thread.sleep(500); // Wait for attachment to establish
                    } catch (Exception ie) {
                        log.warn("Failed to re-establish connection: {}", ie.getMessage());
                    }
                }
            }
        }
        
        // All retries failed
        throw new IOException("Failed to send command after " + maxRetries + " attempts", lastException);
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
            log.info("Command sent successfully to container {}: '{}'", 
                    serverConfig.getServerName(), command);
        } catch (IOException e) {
            log.warn("IOException while writing to stdin stream: {}", e.getMessage());
            // Write failed - connection is likely broken, throw to trigger retry
            throw new IOException("Failed to write command to stdin", e);
        }
    }
}
