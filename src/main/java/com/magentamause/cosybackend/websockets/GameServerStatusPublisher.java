package com.magentamause.cosybackend.websockets;

import com.magentamause.cosybackend.entities.GameServerEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerStatusPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishStatus(String serverUuid, GameServerEntity.GameServerStatus status) {
        String topic = String.format("/topics/game-servers/%s/status", serverUuid);

        log.debug("Publishing status update to {}: {}", topic, status);
        messagingTemplate.convertAndSend(topic, status);
    }
}
