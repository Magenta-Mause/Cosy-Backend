package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.PortMapping;
import com.magentamause.cosybackend.exceptions.PortInUseException;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.repositories.projections.GameServerPortUsage;
import com.magentamause.cosybackend.services.engine.EngineManager;
import com.magentamause.cosybackend.services.engine.PublishedPort;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Detects host port collisions before they turn into an unexplained container start failure.
 *
 * <p>Two servers publishing the same host port cannot run at the same time — the second container
 * to start is rejected by the engine with a message the user never sees in context. This checker
 * catches the collision up front and names the port.
 *
 * <p>The check is best effort by nature: it reads a snapshot, and the port can be taken again
 * between the check and the actual bind. The engine remains the final arbiter; this only makes the
 * common case explainable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerPortChecker {

    private final EngineManager engineManager;
    private final GameServerRepository gameServerRepository;

    /**
     * A wanted host port that something else already holds.
     *
     * @param occupiedBy human-readable description of the occupant, for server-side logs only
     */
    public record PortConflict(int port, PortMapping.PortProtocol protocol, String occupiedBy) {

        /** The port alone — safe to show to the caller. */
        public String describePort() {
            return port + "/" + protocol;
        }

        /** Port plus occupant — for the application log, never for an API response. */
        public String describe() {
            return describePort() + " (in use by " + occupiedBy + ")";
        }
    }

    /**
     * Fails the start when a host port the server needs is currently bound.
     *
     * @throws PortInUseException if at least one port is taken
     */
    public void assertPortsAvailable(GameServerEntity server) {
        List<PortConflict> conflicts = findBlockingConflicts(server);
        if (conflicts.isEmpty()) {
            return;
        }
        log.warn(
                "Refusing to start game server '{}' ({}): port conflicts {}",
                server.getServerName(),
                server.getUuid(),
                conflicts.stream().map(PortConflict::describe).toList());
        throw new PortInUseException(conflicts);
    }

    /**
     * Ports that are occupied right now, by a container on the engine or by another Cosy server
     * that is starting or running.
     *
     * <p>Both sources are needed: a server that is pulling its image has no container yet but has
     * already claimed its ports, while a container Cosy does not manage never appears in the
     * database.
     */
    public List<PortConflict> findBlockingConflicts(GameServerEntity server) {
        Set<PortKey> wanted = portKeys(server);
        if (wanted.isEmpty()) {
            return List.of();
        }

        Map<PortKey, PortConflict> conflicts = new LinkedHashMap<>();
        collectEngineConflicts(server, wanted, conflicts);
        collectStartedServerConflicts(server, wanted, conflicts);
        return List.copyOf(conflicts.values());
    }

    private void collectEngineConflicts(
            GameServerEntity server, Set<PortKey> wanted, Map<PortKey, PortConflict> conflicts) {
        for (PublishedPort published : engineManager.getPublishedHostPorts()) {
            // A leftover container of this very server is not a conflict: starting it removes or
            // reuses that container rather than competing with it.
            if (published.belongsToGameServer(server.getUuid())) {
                continue;
            }
            PortKey key = new PortKey(published.port(), published.protocol());
            if (wanted.contains(key)) {
                conflicts.putIfAbsent(
                        key,
                        new PortConflict(
                                key.port(),
                                key.protocol(),
                                "container " + published.containerName()));
            }
        }
    }

    private void collectStartedServerConflicts(
            GameServerEntity server, Set<PortKey> wanted, Map<PortKey, PortConflict> conflicts) {
        for (GameServerPortUsage usage : otherServerPortUsages(server)) {
            if (usage.status() == null || usage.status().isStopped()) {
                continue;
            }
            PortKey key = PortKey.of(usage);
            if (wanted.contains(key)) {
                conflicts.putIfAbsent(key, conflictWith(key, usage));
            }
        }
    }

    /**
     * Rejects a configuration that collides with itself: the same host port bound twice can never
     * work, no matter what else is running.
     */
    public void assertNoDuplicatePorts(GameServerEntity server) {
        Set<PortKey> seen = new HashSet<>();
        List<String> duplicates =
                mappings(server).stream()
                        .map(PortKey::of)
                        .filter(key -> !seen.add(key))
                        .map(PortKey::describe)
                        .distinct()
                        .toList();

        if (!duplicates.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Host port bound more than once by this server: "
                            + String.join(", ", duplicates));
        }
    }

    /**
     * Ports another server has configured, regardless of whether that server is running.
     *
     * <p>This is not an error — two servers may share a port as long as they are never started
     * together — so it is reported as advice rather than thrown.
     */
    public List<PortConflict> findConfiguredOverlaps(GameServerEntity server) {
        Set<PortKey> wanted = portKeys(server);
        if (wanted.isEmpty()) {
            return List.of();
        }

        Map<PortKey, PortConflict> overlaps = new LinkedHashMap<>();
        for (GameServerPortUsage usage : otherServerPortUsages(server)) {
            PortKey key = PortKey.of(usage);
            if (wanted.contains(key)) {
                overlaps.putIfAbsent(key, conflictWith(key, usage));
            }
        }
        return List.copyOf(overlaps.values());
    }

    private PortConflict conflictWith(PortKey key, GameServerPortUsage usage) {
        return new PortConflict(
                key.port(), key.protocol(), "game server '" + usage.serverName() + "'");
    }

    private List<GameServerPortUsage> otherServerPortUsages(GameServerEntity server) {
        List<GameServerPortUsage> others = new ArrayList<>();
        for (GameServerPortUsage usage : gameServerRepository.findAllPortUsages()) {
            // A not-yet-persisted server has no uuid; nothing in the database can be it.
            if (!Objects.equals(usage.gameServerUuid(), server.getUuid())) {
                others.add(usage);
            }
        }
        return others;
    }

    private Set<PortKey> portKeys(GameServerEntity server) {
        return mappings(server).stream().map(PortKey::of).collect(Collectors.toSet());
    }

    private List<PortMapping> mappings(GameServerEntity server) {
        return Optional.ofNullable(server.getPortMappings()).orElse(List.of());
    }

    private record PortKey(int port, PortMapping.PortProtocol protocol) {
        static PortKey of(PortMapping mapping) {
            return new PortKey(mapping.getInstancePort(), mapping.getProtocol());
        }

        static PortKey of(GameServerPortUsage usage) {
            return new PortKey(usage.instancePort(), usage.protocol());
        }

        String describe() {
            return port + "/" + protocol;
        }
    }
}
