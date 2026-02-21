package com.magentamause.cosybackend.websockets;

import com.magentamause.cosybackend.configs.websockets.WebSocketDestinations;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.dtos.websockets.GameServerStatusUpdateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerStatusPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishStatus(String serverUuid, GameServerDto.GameServerStatus status) {
        String topic =
                WebSocketDestinations.Topics.GAME_SERVER_STATUS.replace("{serverId}", serverUuid);
        var payload = new GameServerStatusUpdateDto(serverUuid, status);

        messagingTemplate.convertAndSend(topic, payload);
    }
}
