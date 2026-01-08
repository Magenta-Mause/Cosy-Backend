package com.magentamause.cosybackend.security.websocket;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.services.SecurityContextService;
import com.magentamause.cosybackend.services.UserEntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebsocketVerifier {
	private final Map<String, WebsocketEndpointVerifier> websocketVerifier = new HashMap<>();
	private final SecurityContextService securityContextService;
	private final UserEntityService userEntityService;

	public WebsocketVerifier addVerifier(String channel, WebsocketEndpointVerifier verifier) {
		websocketVerifier.put("^" + channel.replace("{serverId}", "([1-9][0-9]*)") + "$", verifier);
		return this;
	}

	public boolean verify(String channel, StompHeaderAccessor accessor) {
		for (final Map.Entry<String, WebsocketEndpointVerifier> entry : websocketVerifier.entrySet()) {
			Pattern pattern = Pattern.compile(entry.getKey());
			if (pattern.matcher(channel).matches()) {
				UserEntity user = userEntityService.getUserByUuid(Objects.requireNonNull(accessor.getSessionAttributes()).get("userId").toString());
				if (entry.getValue().verify(channel, accessor, securityContextService, user)) {
					return true;
				}
			}
		}
		return false;
	}
}
