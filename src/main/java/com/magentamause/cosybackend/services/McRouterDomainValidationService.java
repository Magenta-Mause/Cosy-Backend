package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.entities.McRouterConfiguration;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.exceptions.McRouterException;
import com.magentamause.cosybackend.services.engine.docker.McRouterContainerService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for validating mc-router domain configurations. Ensures domains are valid and users have
 * appropriate access.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McRouterDomainValidationService {

    private final CosyInstanceSettingsService settingsService;
    private final McRouterContainerService mcRouterContainerService;

    /**
     * Validates mc-router domains for a game server before starting.
     *
     * @param server the game server to validate
     * @param owner the owner of the game server
     * @throws McRouterException if validation fails
     */
    public void validateDomainsForStart(GameServerEntity server, UserEntity owner)
            throws McRouterException {
        List<String> serverDomains = server.getMcRouterDomains();

        // If no domains configured, no validation needed
        if (serverDomains == null || serverDomains.isEmpty()) {
            return;
        }

        // Check if this is a Minecraft server
        if (!mcRouterContainerService.isMinecraftServer(server)) {
            throw new McRouterException(
                    "MC-Router domains can only be configured for Minecraft servers");
        }

        // Check if mc-router is enabled
        McRouterConfiguration config = settingsService.getMcRouterConfiguration();
        if (!config.isEnabled()) {
            throw new McRouterException(
                    "MC-Router is not enabled. Enable MC-Router in instance settings to use domain routing.");
        }

        // Get allowed domains from global config
        Set<String> globalDomains =
                config.getDomains() != null ? Set.copyOf(config.getDomains()) : Set.of();

        // Validate domains are in global list
        List<String> invalidDomains = new ArrayList<>();
        for (String domain : serverDomains) {
            if (!globalDomains.contains(domain)) {
                invalidDomains.add(domain);
            }
        }

        if (!invalidDomains.isEmpty()) {
            throw new McRouterException(
                    "The following domains are not in the global MC-Router domain list: "
                            + String.join(", ", invalidDomains));
        }

        // Validate user has access to domains
        validateUserDomainAccess(owner, serverDomains);
    }

    /**
     * Validates that a user has access to the specified domains.
     *
     * @param user the user to check
     * @param domains the domains to validate
     * @throws McRouterException if user doesn't have access
     */
    public void validateUserDomainAccess(UserEntity user, List<String> domains)
            throws McRouterException {
        // Owner and Admin roles with mcRouterAllowAllDomains=true can use any domain
        if (user.isMcRouterAllowAllDomains()) {
            return;
        }

        // Get user's allowed domains
        Set<String> userAllowedDomains =
                user.getMcRouterAllowedDomains() != null
                        ? Set.copyOf(user.getMcRouterAllowedDomains())
                        : Set.of();

        // Check each domain
        List<String> unauthorizedDomains =
                domains.stream()
                        .filter(domain -> !userAllowedDomains.contains(domain))
                        .collect(Collectors.toList());

        if (!unauthorizedDomains.isEmpty()) {
            throw new McRouterException(
                    "You do not have access to the following domains: "
                            + String.join(", ", unauthorizedDomains));
        }
    }

    /**
     * Gets the list of domains a user is allowed to use.
     *
     * @param user the user
     * @return list of allowed domains
     */
    public List<String> getAllowedDomainsForUser(UserEntity user) {
        McRouterConfiguration config = settingsService.getMcRouterConfiguration();
        List<String> globalDomains = config.getDomains() != null ? config.getDomains() : List.of();

        // If user can use all domains, return the global list
        if (user.isMcRouterAllowAllDomains()) {
            return globalDomains;
        }

        // Otherwise, return the intersection of user's allowed domains and global domains
        Set<String> globalSet = Set.copyOf(globalDomains);
        List<String> userDomains =
                user.getMcRouterAllowedDomains() != null
                        ? user.getMcRouterAllowedDomains()
                        : List.of();

        return userDomains.stream().filter(globalSet::contains).collect(Collectors.toList());
    }

    /**
     * Checks if mc-router is enabled and the user can configure domains.
     *
     * @param user the user
     * @return true if mc-router is available for this user
     */
    public boolean isMcRouterAvailableForUser(UserEntity user) {
        if (!settingsService.isMcRouterEnabled()) {
            return false;
        }

        // User must have either allowAllDomains or specific domains configured
        return user.isMcRouterAllowAllDomains()
                || (user.getMcRouterAllowedDomains() != null
                        && !user.getMcRouterAllowedDomains().isEmpty());
    }
}
