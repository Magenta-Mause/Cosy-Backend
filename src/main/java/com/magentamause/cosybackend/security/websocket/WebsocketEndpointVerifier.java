package com.magentamause.cosybackend.security.websocket;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.services.auth.SecurityContextFilter;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

public interface WebsocketEndpointVerifier {
    boolean verify(
            String url,
            StompHeaderAccessor headers,
            SecurityContextFilter securityContextFilter,
            UserEntity user);
}
