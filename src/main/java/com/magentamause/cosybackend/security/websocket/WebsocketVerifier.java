package com.magentamause.cosybackend.security.websocket;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.services.SecurityContextService;
import com.magentamause.cosybackend.services.UserEntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public class WebsocketVerifier {
	private final Map<String, WebsocketEndpointVerifier> websocketVerifier = new HashMap<>();
	private final SecurityContextService securityContextService;
	private final UserEntityService userEntityService;

	public WebsocketVerifier addVerifier(String channel, WebsocketEndpointVerifier verifier) {
		websocketVerifier.put("^" + channel.replace("{serverId}", "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})") + "$", verifier);
		return this;
	}

	public boolean verify(String channel, StompHeaderAccessor accessor) {
		for (final Map.Entry<String, WebsocketEndpointVerifier> verifier : websocketVerifier.entrySet()) {
			Pattern pattern = Pattern.compile(verifier.getKey());
			log.info("pattern: {}", pattern.toString());
			if (pattern.matcher(channel).matches()) {
				String userId = Objects.requireNonNull(accessor.getSessionAttributes()).get("userId").toString();
				log.info("User: {} trying to access channel: {}", userId, channel);
				UserEntity user = userEntityService.getUserByUuid(userId);
				if (verifier.getValue().verify(channel, accessor, securityContextService, user)) {
					log.info("Access granted for user: {}", userId);
					return true;
				} else {
					log.info("Access denied for user: {}", userId);
				}
			}
		}
		return false;
	}
}
