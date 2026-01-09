package com.magentamause.cosybackend.configs;


import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import com.magentamause.cosybackend.security.websocket.WebsocketVerifier;
import com.magentamause.cosybackend.security.websocket.verifier.AccessManagementVerifier;
import com.magentamause.cosybackend.services.SecurityContextService;
import com.magentamause.cosybackend.services.UserEntityService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebsocketVerifierConfig {

	@Bean
	public WebsocketVerifier websocketVerifier(SecurityContextService securityContextService, UserEntityService userEntityService) {
		return new WebsocketVerifier(securityContextService, userEntityService)
				.addVerifier("/topics/game-server-logs/creation/{serverId}", new AccessManagementVerifier("/topics/game-server-logs/creation/{serverId}", Action.READ, Resource.GAME_SERVER_LOG));
	}
}
