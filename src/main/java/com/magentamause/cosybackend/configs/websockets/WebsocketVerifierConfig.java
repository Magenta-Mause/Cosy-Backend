package com.magentamause.cosybackend.configs.websockets;

import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.ValidatorRegistry;
import com.magentamause.cosybackend.security.websocket.WebsocketVerifier;
import com.magentamause.cosybackend.security.websocket.verifier.AccessManagementVerifier;
import com.magentamause.cosybackend.services.auth.SecurityContextService;
import com.magentamause.cosybackend.services.user.UserEntityService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebsocketVerifierConfig {

    @Bean
    public WebsocketVerifier websocketVerifier(
            SecurityContextService securityContextService,
            UserEntityService userEntityService,
            ObjectProvider<ValidatorRegistry> validatorRegistryProvider,
            ObjectProvider<ResourceResolver> resourceResolverProvider) {
        return new WebsocketVerifier(securityContextService, userEntityService)
                .addVerifier(
                        new AccessManagementVerifier(
                                WebSocketDestinations.Topics.GAME_SERVER_LOGS_CREATION,
                                Operation.GAME_SERVER_LOG_READ,
                                validatorRegistryProvider::getObject,
                                resourceResolverProvider::getObject))
                .addVerifier(
                        new AccessManagementVerifier(
                                WebSocketDestinations.Topics.GAME_SERVER_UPDATES,
                                Operation.GAME_SERVER_GET,
                                validatorRegistryProvider::getObject,
                                resourceResolverProvider::getObject))
                .addVerifier(
                        new AccessManagementVerifier(
                                WebSocketDestinations.Topics.GAME_SERVER_DOCKER_PROGRESS,
                                Operation.GAME_SERVER_GET,
                                validatorRegistryProvider::getObject,
                                resourceResolverProvider::getObject))
                .addVerifier(
                        new AccessManagementVerifier(
                                WebSocketDestinations.Topics.GAME_SERVER_METRICS,
                                Operation.GAME_SERVER_METRIC_READ,
                                validatorRegistryProvider::getObject,
                                resourceResolverProvider::getObject))
                .addVerifier(
                        new AccessManagementVerifier(
                                WebSocketDestinations.Topics.GAME_SERVER_PERMISSIONS_CONFIG_CHANGE,
                                Operation.USER_READ_PERMISSIONS,
                                validatorRegistryProvider::getObject,
                                resourceResolverProvider::getObject,
                                "{userId}"));
    }
}
