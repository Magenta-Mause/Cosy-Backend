package com.magentamause.cosybackend.security.websocket.verifier;

import com.magentamause.cosybackend.configs.UtilConfig;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.ValidatorRegistry;
import com.magentamause.cosybackend.security.websocket.WebsocketEndpointVerifier;
import com.magentamause.cosybackend.services.auth.SecurityContextService;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

public class AccessManagementVerifier implements WebsocketEndpointVerifier {

    private final Pattern pathPattern;
    private final Operation operation;
    private final Supplier<ValidatorRegistry> validatorRegistrySupplier;
    private final Supplier<ResourceResolver> resourceResolverSupplier;

    public AccessManagementVerifier(
            final String path,
            final Operation operation,
            final Supplier<ValidatorRegistry> validatorRegistrySupplier,
            final Supplier<ResourceResolver> resourceResolverSupplier) {
        this.pathPattern =
                Pattern.compile("^" + path.replace("{serverId}", UtilConfig.UUID_REGEX) + "$");
        this.operation = operation;
        this.validatorRegistrySupplier = validatorRegistrySupplier;
        this.resourceResolverSupplier = resourceResolverSupplier;
    }

    @Override
    public boolean verify(
            String url,
            StompHeaderAccessor headers,
            SecurityContextService securityContextService,
            UserEntity user) {
        Matcher matcher = pathPattern.matcher(url);
        if (matcher.matches()) {
            final String serverId = matcher.group(1);
            return validatorRegistrySupplier
                    .get()
                    .getValidator(operation)
                    .invoke(resourceResolverSupplier.get(), serverId, user);
        }
        return false;
    }
}
