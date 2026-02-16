package com.magentamause.cosybackend.security.websocket;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.services.auth.SecurityContextService;
import com.magentamause.cosybackend.services.user.UserEntityService;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

@Slf4j
@RequiredArgsConstructor
public class WebsocketVerifier {
    private final Map<Pattern, WebsocketEndpointVerifier> websocketVerifier = new HashMap<>();
    private final SecurityContextService securityContextService;
    private final UserEntityService userEntityService;

    public WebsocketVerifier addVerifier(WebsocketEndpointVerifier verifier) {
        websocketVerifier.put(verifier.getPathPattern(), verifier);
        return this;
    }

    public boolean verify(String channel, StompHeaderAccessor stompHeaders) {
        final var registeredVerifiers = websocketVerifier.entrySet();
        log.debug("Verifying channel: {}", channel);
        for (final var verifier : registeredVerifiers) {
            Pattern verifierPattern = verifier.getKey();
            if (stompHeaders == null) {
                log.warn("No headers found for channel: {}", channel);
                continue;
            }
            if (!verifierPattern.matcher(channel).matches()) {
                continue;
            }
            String userId = extractUserId(stompHeaders);
            if (userId == null) {
                log.debug("No user found for channel: {}", channel);
                continue;
            }
            log.debug("User: {} trying to access channel: {}", userId, channel);
            UserEntity user = userEntityService.getUserByUuid(userId);
            return verifier.getValue().verify(channel, stompHeaders, securityContextService, user);
        }
        return false;
    }

    private String extractUserId(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return null;
        }
        try {
            return (String) sessionAttributes.get("userId");
        } catch (ClassCastException e) {
            return null;
        }
    }
}
