package com.magentamause.cosybackend.security.websocket.verifier;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import com.magentamause.cosybackend.security.websocket.WebsocketEndpointVerifier;
import com.magentamause.cosybackend.security.websocket.WebsocketVerifier;
import com.magentamause.cosybackend.services.SecurityContextService;
import com.magentamause.cosybackend.services.UserEntityService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Builder
public class AccessManagementVerifier implements WebsocketEndpointVerifier {

	private Pattern pathPattern;
	private Action action;
	private Resource resource;

	public AccessManagementVerifier(final String path, final Action action, final Resource resource) {
		this.pathPattern = Pattern.compile("^" + path.replace("{serverId}", "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})") + "$");
		this.action = action;
		this.resource = resource;
	}

	@Override
	public boolean verify(String url, StompHeaderAccessor headers, SecurityContextService securityContextService, UserEntity user) {
		Matcher matcher = pathPattern.matcher(url);
		if (matcher.matches()) {
			final String serverId = matcher.group(1);
			securityContextService.canUser(action, resource, serverId, user);
			return true;
		}
		return false;
	}
}
