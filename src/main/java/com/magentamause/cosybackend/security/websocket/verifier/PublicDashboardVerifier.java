package com.magentamause.cosybackend.security.websocket.verifier;

import com.magentamause.cosybackend.configs.UtilConfig;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.websocket.WebsocketEndpointVerifier;
import com.magentamause.cosybackend.services.auth.SecurityContextService;
import com.magentamause.cosybackend.services.core.gameserver.GameServerService;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

public class PublicDashboardVerifier implements WebsocketEndpointVerifier {

    @Getter private final Pattern pathPattern;
    private final Supplier<GameServerService> gameServerServiceSupplier;

    public PublicDashboardVerifier(
            final String path, final Supplier<GameServerService> gameServerServiceSupplier) {
        this.pathPattern =
                Pattern.compile("^" + path.replace("{serverId}", UtilConfig.UUID_REGEX) + "$");
        this.gameServerServiceSupplier = gameServerServiceSupplier;
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
            return gameServerServiceSupplier.get().isGameServerPubliclyEvaluable(serverId);
        }
        return false;
    }
}
