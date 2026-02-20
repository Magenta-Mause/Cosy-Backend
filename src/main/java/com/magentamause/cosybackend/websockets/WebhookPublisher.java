package com.magentamause.cosybackend.websockets;

import com.magentamause.cosybackend.configs.websockets.WebSocketDestinations;
import com.magentamause.cosybackend.dtos.entitydtos.WebhookDto;
import com.magentamause.cosybackend.dtos.websockets.WebhookEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishChange(String serverUuid, WebhookDto webhook) {
        String topic =
                WebSocketDestinations.Topics.GAME_SERVER_WEBHOOKS.replace("{serverId}", serverUuid);
        var payload = new WebhookEventDto(serverUuid, webhook);

        log.info("Publishing webhook change to {}: {}", topic, webhook);
        messagingTemplate.convertAndSend(topic, payload);
    }
}
