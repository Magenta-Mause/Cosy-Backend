package com.magentamause.cosybackend.websockets;

import com.magentamause.cosybackend.configs.websockets.WebSocketDestinations;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerLogWebsocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishLog(String serverUuid, GameServerLogMessageEntity logMessage) {
        String topic =
                WebSocketDestinations.Topics.GAME_SERVER_LOGS_CREATION.replace(
                        "{serverId}", serverUuid);
        messagingTemplate.convertAndSend(topic, logMessage);
    }
}
