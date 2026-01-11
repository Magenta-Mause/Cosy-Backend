package com.magentamause.cosybackend.security.websocket;

import com.magentamause.cosybackend.configs.UtilConfig;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.services.SecurityContextService;
import com.magentamause.cosybackend.services.UserEntityService;
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

    public WebsocketVerifier addVerifier(String channel, WebsocketEndpointVerifier verifier) {
        String regex =
                "^"
                        + channel.replace(
                                "{serverId}",
                                UtilConfig.UUID_REGEX)
                        + "$";
        Pattern pattern = Pattern.compile(regex);
        websocketVerifier.put(pattern, verifier);
        return this;
    }

    public boolean verify(String channel, StompHeaderAccessor stompHeaders) {
        final var registeredVerifiers = websocketVerifier.entrySet();
        for (final var verifier : registeredVerifiers) {
            Pattern verifierPattern = verifier.getKey();
            if (stompHeaders == null) {
                continue;
            }
            if (!verifierPattern.matcher(channel).matches()) {
                continue;
            }
            String userId = extractUserId(stompHeaders);
            if (userId == null) {
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
