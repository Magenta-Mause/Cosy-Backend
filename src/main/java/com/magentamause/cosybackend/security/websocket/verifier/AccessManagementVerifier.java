package com.magentamause.cosybackend.security.websocket.verifier;

import com.magentamause.cosybackend.configs.UtilConfig;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import com.magentamause.cosybackend.security.websocket.WebsocketEndpointVerifier;
import com.magentamause.cosybackend.services.SecurityContextService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

public class AccessManagementVerifier implements WebsocketEndpointVerifier {

    private final Pattern pathPattern;
    private final Action action;
    private final Resource resource;

    public AccessManagementVerifier(
            final String path, final Action action, final Resource resource) {
        this.pathPattern =
                Pattern.compile(
                        "^"
                                + path.replace(
                                "{serverId}",
                                UtilConfig.UUID_REGEX)
                                + "$");
        this.action = action;
        this.resource = resource;
    }

    @Override
    public boolean verify(
            String url,
            StompHeaderAccessor headers,
            SecurityContextService securityContextService,
            UserEntity user) {
        Matcher matcher = pathPattern.matcher(url);
        if (matcher.matches()) {
            final String serverId = matcher.group(1);
            return securityContextService.canUser(action, resource, serverId, user);
        }
        return false;
    }
}
