package com.magentamause.cosybackend.configs.websockets;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WebSocketDestinations {

    public static final String BROKER_PREFIX = "/topics";
    public static final String APP_PREFIX = "/v1/app";
    public static final String ENDPOINT = "/v1/ws";

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Topics {
        public static final String GAME_SERVER_LOGS_CREATION =
                BROKER_PREFIX + "/game-servers/{serverId}/logs";
        public static final String GAME_SERVER_METRICS =
                BROKER_PREFIX + "/game-servers/{serverId}/metrics";
        public static final String GAME_SERVER_UPDATES =
                BROKER_PREFIX + "/game-servers/updates/{serverId}";
        public static final String GAME_SERVER_DOCKER_PROGRESS =
                BROKER_PREFIX + "/game-servers/docker-progress/{serverId}";
        public static final String GAME_SERVER_PERMISSIONS_CONFIG_CHANGE =
                BROKER_PREFIX + "/game-servers/permissions-config-change/{userId}";
    }
}
