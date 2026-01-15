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
        public static final String GAME_SERVER_LOGS_CREATION = BROKER_PREFIX + "/game-server-logs/creation/{serverId}";
        public static final String GAME_SERVER_STATUS = BROKER_PREFIX + "/game-servers/status/{serverId}";
        public static final String GAME_SERVER_DOCKER_PROGRESS = BROKER_PREFIX + "/game-servers/docker-progress/{serverId}";
    }
}
