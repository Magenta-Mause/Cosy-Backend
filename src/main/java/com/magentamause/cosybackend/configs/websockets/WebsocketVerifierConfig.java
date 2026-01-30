package com.magentamause.cosybackend.configs.websockets;

import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import com.magentamause.cosybackend.security.websocket.WebsocketVerifier;
import com.magentamause.cosybackend.security.websocket.verifier.AccessManagementVerifier;
import com.magentamause.cosybackend.services.auth.SecurityContextService;
import com.magentamause.cosybackend.services.user.UserEntityService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebsocketVerifierConfig {

    @Bean
    public WebsocketVerifier websocketVerifier(
            SecurityContextService securityContextService, UserEntityService userEntityService) {
        return new WebsocketVerifier(securityContextService, userEntityService)
                .addVerifier(
                        WebSocketDestinations.Topics.GAME_SERVER_LOGS_CREATION,
                        new AccessManagementVerifier(
                                WebSocketDestinations.Topics.GAME_SERVER_LOGS_CREATION,
                                Action.READ,
                                Resource.GAME_SERVER_LOG))
                .addVerifier(
                        WebSocketDestinations.Topics.GAME_SERVER_STATUS,
                        new AccessManagementVerifier(
                                WebSocketDestinations.Topics.GAME_SERVER_STATUS,
                                Action.READ,
                                Resource.GAME_SERVER))
                .addVerifier(
                        WebSocketDestinations.Topics.GAME_SERVER_DOCKER_PROGRESS,
                        new AccessManagementVerifier(
                                WebSocketDestinations.Topics.GAME_SERVER_DOCKER_PROGRESS,
                                Action.READ,
                                Resource.GAME_SERVER))
                .addVerifier(
                        WebSocketDestinations.Topics.GAME_SERVER_METRICS,
                        new AccessManagementVerifier(
                                WebSocketDestinations.Topics.GAME_SERVER_METRICS,
                                Action.READ,
                                Resource.GAME_SERVER));
    }
}
