package com.magentamause.cosybackend.security.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final WebsocketVerifier websocketVerifier;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String subscribedEndpoint = accessor.getDestination();
            log.debug("Subscribed to {}", subscribedEndpoint);
            try {
                if (websocketVerifier.verify(subscribedEndpoint, accessor)) {
                    log.debug("Authentication successful for {}", subscribedEndpoint);
                    return message;
                } else {
                    log.debug("Missing Authentication for {}", subscribedEndpoint);
                    return null;
                }
            } catch (Exception e) {
                log.error("Error while verifying authentication for {}", subscribedEndpoint, e);
            }
            return null;
        }
        if (StompCommand.SEND.equals(accessor.getCommand())) {
            String userId = extractUserId(accessor.getSessionAttributes());
            if (userId == null) {
                log.debug("Blocking anonymous SEND to {}", accessor.getDestination());
                return null;
            }
        }
        return message;
    }

    private String extractUserId(java.util.Map<String, Object> sessionAttributes) {
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
