package com.magentamause.cosybackend.websockets;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.dtos.entitydtos.PullProgressDto;
import com.magentamause.cosybackend.dtos.websockets.GameServerDockerProgressUpdateDto;
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
        String topic = String.format("/topics/game-servers/%s/status", serverUuid);
        var payload = new GameServerStatusUpdateDto(serverUuid, status);

        log.debug("Publishing status update to {}: {}", topic, status);
        messagingTemplate.convertAndSend(topic, payload);
    }

    public void publishPullProgress(String serverUuid, PullProgressDto progress) {
        String topic = String.format("/topics/game-servers/%s/docker-progress", serverUuid);
        var payload = new GameServerDockerProgressUpdateDto(serverUuid, progress);
        // log.debug("Publishing pull progress to {}", topic); // verbose
        messagingTemplate.convertAndSend(topic, payload);
    }
}
