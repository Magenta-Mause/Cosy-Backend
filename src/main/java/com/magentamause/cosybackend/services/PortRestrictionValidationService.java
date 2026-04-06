package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.configs.properties.EngineProperties;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.PortMapping;
import com.magentamause.cosybackend.exceptions.PortRestrictionException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortRestrictionValidationService {

    private final EngineProperties engineProperties;

    /**
     * Validates that all instance ports on a game server are allowed for the given user.
     *
     * @param server the game server to validate
     * @param owner the owner of the game server
     * @throws PortRestrictionException if any port is not allowed
     */
    public void validatePortsForServer(GameServerEntity server, UserEntity owner) {
        List<PortMapping> portMappings = server.getPortMappings();
        if (portMappings == null || portMappings.isEmpty()) {
            return;
        }

        // Check globally blocked ports first (applies to all users)
        List<Integer> blockedPorts = engineProperties.blockedPorts();
        if (blockedPorts != null && !blockedPorts.isEmpty()) {
            List<Integer> blockedUsed = new ArrayList<>();
            for (PortMapping mapping : portMappings) {
                if (blockedPorts.contains(mapping.getInstancePort())) {
                    blockedUsed.add(mapping.getInstancePort());
                }
            }
            if (!blockedUsed.isEmpty()) {
                throw new PortRestrictionException(
                        "The following ports are globally blocked: "
                                + blockedUsed.stream()
                                        .map(Object::toString)
                                        .reduce((a, b) -> a + ", " + b)
                                        .orElse(""));
            }
        }

        // Check user-specific port restrictions
        if (!owner.isPortRestrictionsEnabled()) {
            return;
        }

        List<String> allowedPortRanges =
                owner.getAllowedPorts() != null ? owner.getAllowedPorts() : List.of();

        List<Integer> disallowedPorts = new ArrayList<>();
        for (PortMapping mapping : portMappings) {
            if (!isPortAllowed(mapping.getInstancePort(), allowedPortRanges)) {
                disallowedPorts.add(mapping.getInstancePort());
            }
        }

        if (!disallowedPorts.isEmpty()) {
            throw new PortRestrictionException(
                    "The following ports are not allowed for this user: "
                            + disallowedPorts.stream()
                                    .map(Object::toString)
                                    .reduce((a, b) -> a + ", " + b)
                                    .orElse(""));
        }
    }

    private boolean isPortAllowed(int port, List<String> allowedPortRanges) {
        for (String range : allowedPortRanges) {
            if (range.contains("-")) {
                String[] parts = range.split("-", 2);
                try {
                    int start = Integer.parseInt(parts[0].trim());
                    int end = Integer.parseInt(parts[1].trim());
                    if (port >= start && port <= end) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid port range format: {}", range);
                }
            } else {
                try {
                    int allowedPort = Integer.parseInt(range.trim());
                    if (port == allowedPort) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid port format: {}", range);
                }
            }
        }
        return false;
    }
}
