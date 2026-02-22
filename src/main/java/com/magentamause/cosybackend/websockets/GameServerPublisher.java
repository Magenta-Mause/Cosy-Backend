package com.magentamause.cosybackend.websockets;

import com.magentamause.cosybackend.configs.websockets.WebSocketDestinations;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishGameServer(String serverUuid, GameServerDto gameServer) {
        String topic =
                WebSocketDestinations.Topics.GAME_SERVER_UPDATES.replace("{serverId}", serverUuid);

        log.info("Publishing game server update to {}: {}", topic, gameServer.getUuid());
        messagingTemplate.convertAndSend(topic, gameServer);
    }
}
