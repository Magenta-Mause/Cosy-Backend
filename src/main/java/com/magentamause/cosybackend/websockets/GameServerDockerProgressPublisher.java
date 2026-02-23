package com.magentamause.cosybackend.websockets;

import com.magentamause.cosybackend.configs.websockets.WebSocketDestinations;
import com.magentamause.cosybackend.dtos.entitydtos.PullProgressDto;
import com.magentamause.cosybackend.dtos.websockets.GameServerDockerProgressUpdateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class GameServerDockerProgressPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishDockerProgress(String serverUuid, PullProgressDto pullProgress) {
        log.debug("Publishing log message to websocket for server {}: {}", serverUuid, serverUuid);
        String topic =
                WebSocketDestinations.Topics.GAME_SERVER_DOCKER_PROGRESS.replace(
                        "{serverId}", serverUuid);
        GameServerDockerProgressUpdateDto payload =
                new GameServerDockerProgressUpdateDto(serverUuid, pullProgress);
        messagingTemplate.convertAndSend(topic, payload);
    }
}
