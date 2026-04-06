package com.magentamause.cosybackend.services.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.CreateNetworkResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import com.magentamause.cosybackend.configs.properties.EngineProperties;
import com.magentamause.cosybackend.configs.properties.McRouterProperties;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.dtos.entitydtos.McRouterStatusDto;
import com.magentamause.cosybackend.entities.McRouterConfiguration;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.PortMapping;
import com.magentamause.cosybackend.exceptions.McRouterException;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.services.CosyInstanceSettingsService;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for managing the mc-router Docker container. MC-Router enables multiple game servers to
 * share a single port with domain-based routing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McRouterContainerService {

    private final DockerClient dockerClient;
    private final GameServerRepository gameServerRepository;
    private final CosyInstanceSettingsService settingsService;
    private final McRouterProperties mcRouterProperties;
    private final EngineProperties engineProperties;

    private Closeable mcRouterLogCallback;

    /**
     * Ensures the cosy-network Docker network exists. Creates it if it doesn't exist.
     *
     * @return the network ID
     */
    public String ensureNetworkExists() {
        String networkName = engineProperties.docker().networkName();
        Optional<Network> existingNetwork = findNetwork(networkName);
        if (existingNetwork.isPresent()) {
            log.debug(
                    "Network {} already exists with ID: {}",
                    networkName,
                    existingNetwork.get().getId());
            return existingNetwork.get().getId();
        }

        log.info("Creating Docker network: {}", networkName);
        CreateNetworkResponse response =
                dockerClient
                        .createNetworkCmd()
                        .withName(networkName)
                        .withDriver("bridge")
                        .exec();
        log.info("Created network {} with ID: {}", networkName, response.getId());
        return response.getId();
    }

    /**
     * Finds a Docker network by name.
     *
     * @param networkName the name of the network
     * @return the network if found
     */
    private Optional<Network> findNetwork(String networkName) {
        return dockerClient.listNetworksCmd().withNameFilter(networkName).exec().stream()
                .filter(n -> networkName.equals(n.getName()))
                .findFirst();
    }

    /**
     * Starts the mc-router container if not already running.
     *
     * @throws McRouterException if there's a port conflict or start failure
     */
    public synchronized void startMcRouter() throws McRouterException {
        McRouterConfiguration config = settingsService.getMcRouterConfiguration();
        if (!config.isEnabled()) {
            throw new McRouterException("MC-Router is not enabled");
        }

        Optional<Container> existingContainer = findMcRouterContainer();
        if (existingContainer.isPresent()) {
            String state = existingContainer.get().getState();
            if ("running".equalsIgnoreCase(state)) {
                log.info("MC-Router container is already running");
                return;
            }
            // Remove non-running container to recreate with possibly updated config
            log.info("Removing existing non-running mc-router container");
            removeMcRouterContainer();
        }

        int port = config.getPort() > 0 ? config.getPort() : mcRouterProperties.defaultPort();

        // Check for port conflicts with COSY game servers
        checkPortConflicts(port);

        // Ensure network exists
        ensureNetworkExists();

        log.info("Starting MC-Router container on port {}", port);

        String image = mcRouterProperties.image();
        String networkName = engineProperties.docker().networkName();

        // Pull image if needed
        try {
            dockerClient.inspectImageCmd(image).exec();
        } catch (NotFoundException e) {
            log.info("Pulling MC-Router image: {}", image);
            try {
                dockerClient.pullImageCmd(image).start().awaitCompletion();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new McRouterException("Interrupted while pulling MC-Router image", ie);
            }
        }

        // Create port bindings
        ExposedPort exposedPort = ExposedPort.tcp(mcRouterProperties.defaultPort());
        Ports portBindings = new Ports();
        portBindings.bind(exposedPort, Ports.Binding.bindPort(port));

        // Create host config with network, port bindings, and Docker socket access
        HostConfig hostConfig =
                HostConfig.newHostConfig()
                        .withNetworkMode(networkName)
                        .withPortBindings(portBindings)
                        .withBinds(
                                new Bind(
                                        "/var/run/docker.sock", new Volume("/var/run/docker.sock")))
                        .withExtraHosts("host.docker.internal:host-gateway");

        // Create container with Docker discovery mode enabled
        CreateContainerResponse response =
                dockerClient
                        .createContainerCmd(image)
                        .withName(mcRouterProperties.containerName())
                        .withEnv("IN_DOCKER=true") // Enable Docker discovery mode
                        .withExposedPorts(exposedPort)
                        .withHostConfig(hostConfig)
                        .withLabels(Map.of("cosy.managed", "true", "cosy.service", "mc-router"))
                        .exec();

        // Start the container
        try {
            dockerClient.startContainerCmd(response.getId()).exec();
            log.info("MC-Router container started successfully with ID: {}", response.getId());

            attachMcRouterLogListener(response.getId());

        } catch (Exception e) {
            // Clean up on failure
            try {
                dockerClient.removeContainerCmd(response.getId()).withForce(true).exec();
            } catch (Exception cleanupEx) {
                log.warn("Failed to cleanup mc-router container after start failure", cleanupEx);
            }
            throw new McRouterException(
                    "Failed to start MC-Router container: " + e.getMessage(), e);
        }
    }

    private void attachMcRouterLogListener(String containerId) {
        detachMcRouterLogListener();
        mcRouterLogCallback =
                dockerClient
                        .logContainerCmd(containerId)
                        .withStdOut(true)
                        .withStdErr(true)
                        .withFollowStream(true)
                        .withTailAll()
                        .exec(
                                new ResultCallback.Adapter<Frame>() {
                                    @Override
                                    public void onNext(Frame frame) {
                                        String message =
                                                new String(
                                                                frame.getPayload(),
                                                                StandardCharsets.UTF_8)
                                                        .stripTrailing();
                                        log.info("[mc-router] {}", message);
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {
                                        log.warn("MC-Router log stream error", throwable);
                                    }
                                });
    }

    private void detachMcRouterLogListener() {
        if (mcRouterLogCallback != null) {
            try {
                mcRouterLogCallback.close();
            } catch (IOException e) {
                log.warn("Failed to close MC-Router log listener: {}", e.getMessage());
            }
            mcRouterLogCallback = null;
        }
    }

    /**
     * Ensures mc-router is running if it's enabled and there are running servers with domains. Call
     * this when a server with domains starts or when mc-router is enabled.
     *
     * @throws McRouterException if mc-router fails to start
     */
    public void ensureMcRouterRunningIfNeeded() throws McRouterException {
        log.info("Ensuring MC-Router is running if needed");
        if (!settingsService.isMcRouterEnabled()) {
            log.info("MC-Router is not enabled");
            return;
        }
        if (getRunningServersWithDomains().isEmpty()) {
            log.info("No running servers with MC-Router domains");
            return;
        }
        log.info("Starting MC-Router if needed");
        startMcRouter();
    }

    public void ensureMcRouterRunningIfNeeded(GameServerEntity currentlyStartingGameServer)
            throws McRouterException {
        if (!settingsService.isMcRouterEnabled()) {
            log.info("MC-Router is not enabled");
            return;
        }
        if (currentlyStartingGameServer != null
                && hasMcRouterDomains(currentlyStartingGameServer)) {
            startMcRouter();
        } else {
            ensureMcRouterRunningIfNeeded();
        }
    }

    /**
     * Stops mc-router if no running servers with domains need it. Call this after a server with
     * domains stops.
     */
    public void stopMcRouterIfNoServersNeedIt() {
        if (getRunningServersWithDomains().isEmpty()) {
            log.info(
                    "No running servers with MC-Router domains, stopping MC-Router container");
            removeMcRouterContainer();
        }
    }

    /** Removes the mc-router container if it exists. */
    public void removeMcRouterContainer() {
        detachMcRouterLogListener();
        findMcRouterContainer()
                .ifPresent(
                        container -> {
                            log.info("Stopping and removing MC-Router container");
                            try {
                                if ("running".equalsIgnoreCase(container.getState())) {
                                    dockerClient.stopContainerCmd(container.getId()).exec();
                                }
                            } catch (Exception e) {
                                log.warn("Error stopping mc-router container, forcing removal", e);
                            }
                            try {
                                dockerClient
                                        .removeContainerCmd(container.getId())
                                        .withForce(true)
                                        .exec();
                            } catch (Exception e) {
                                log.error("Failed to remove mc-router container", e);
                            }
                        });
    }

    /**
     * Finds the mc-router container.
     *
     * @return the container if found
     */
    public Optional<Container> findMcRouterContainer() {
        String nameToMatch = "/" + mcRouterProperties.containerName();
        return dockerClient.listContainersCmd().withShowAll(true).exec().stream()
                .filter(
                        c ->
                                Arrays.asList(
                                                Optional.ofNullable(c.getNames())
                                                        .orElse(new String[0]))
                                        .contains(nameToMatch))
                .findFirst();
    }

    /**
     * Gets the current status of the mc-router container.
     *
     * @return the status DTO
     */
    public McRouterStatusDto getStatus() {
        McRouterConfiguration config = settingsService.getMcRouterConfiguration();
        Optional<Container> container = findMcRouterContainer();

        boolean isRunning =
                container.map(c -> "running".equalsIgnoreCase(c.getState())).orElse(false);
        String containerId = container.map(Container::getId).orElse(null);

        return McRouterStatusDto.builder()
                .enabled(config.isEnabled())
                .port(config.getPort() > 0 ? config.getPort() : mcRouterProperties.defaultPort())
                .running(isRunning)
                .containerId(containerId)
                .build();
    }

    /**
     * Checks if the specified port is in use by any COSY-managed game server.
     *
     * @param port the port to check
     * @throws McRouterException if the port is in use
     */
    private void checkPortConflicts(int port) throws McRouterException {
        Set<Integer> portsInUse = getPortsInUseByGameServers();
        if (portsInUse.contains(port)) {
            throw new McRouterException(
                    "Port "
                            + port
                            + " is already in use by a COSY game server. "
                            + "Please stop the conflicting server or change the MC-Router port.");
        }
    }

    /**
     * Gets all instance ports currently in use by game servers.
     *
     * @return set of ports in use
     */
    public Set<Integer> getPortsInUseByGameServers() {
        return gameServerRepository.findAll().stream()
                .filter(gs -> gs.getPortMappings() != null)
                .filter(gs -> !gs.getStatus().isStopped())
                .flatMap(gs -> gs.getPortMappings().stream())
                .map(PortMapping::getInstancePort)
                .collect(Collectors.toSet());
    }

    /**
     * Gets all running servers that have mc-router domains configured.
     *
     * @return list of servers with domains
     */
    public List<GameServerEntity> getRunningServersWithDomains() {
        return gameServerRepository.findAll().stream()
                .filter(this::isRunning)
                .filter(this::hasMcRouterDomains)
                .toList();
    }

    /**
     * Gets all servers (regardless of status) that have mc-router domains configured.
     *
     * @return list of servers with domains
     */
    public List<GameServerEntity> getServersWithDomains() {
        return gameServerRepository.findAll().stream()
                .filter(this::hasMcRouterDomains)
                .toList();
    }

    /**
     * Checks if a game server is currently running.
     *
     * @param server the game server
     * @return true if running
     */
    private boolean isRunning(GameServerEntity server) {
        return server.getStatus() == GameServerDto.GameServerStatus.RUNNING;
    }

    /**
     * Checks if a game server has mc-router domains configured.
     *
     * @param server the game server
     * @return true if it has domains
     */
    private boolean hasMcRouterDomains(GameServerEntity server) {
        return server.getMcRouterDomains() != null && !server.getMcRouterDomains().isEmpty();
    }

    /**
     * Connects a container to the cosy-network.
     *
     * @param containerId the container ID to connect
     */
    public void connectContainerToNetwork(String containerId) {
        String networkName = engineProperties.docker().networkName();
        ensureNetworkExists();
        try {
            dockerClient
                    .connectToNetworkCmd()
                    .withContainerId(containerId)
                    .withNetworkId(networkName)
                    .exec();
            log.debug("Connected container {} to network {}", containerId, networkName);
        } catch (ConflictException e) {
            log.debug(
                    "Container {} is already connected to network {}",
                    containerId,
                    networkName);
        }
    }

    /**
     * Gets the network name for use by other services.
     *
     * @return the network name
     */
    public String getNetworkName() {
        return engineProperties.docker().networkName();
    }

    /**
     * Gets the mc-router container name.
     *
     * @return the container name
     */
    public String getContainerName() {
        return mcRouterProperties.containerName();
    }
}
