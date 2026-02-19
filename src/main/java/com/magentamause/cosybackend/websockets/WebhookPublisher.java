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

    public void publishCreated(String serverUuid, WebhookDto webhook) {
        publish("webhook.created", serverUuid, webhook);
    }

    public void publishUpdated(String serverUuid, WebhookDto webhook) {
        publish("webhook.updated", serverUuid, webhook);
    }

    public void publishDeleted(String serverUuid, String webhookUuid) {
        var topic =
                WebSocketDestinations.Topics.GAME_SERVER_WEBHOOKS.replace("{serverId}", serverUuid);
        var payload = new WebhookEventDto("webhook.deleted", serverUuid, null);

        log.debug("Publishing webhook.deleted to {}: webhookUuid={}", topic, webhookUuid);
        messagingTemplate.convertAndSend(topic, payload);
    }

    private void publish(String eventType, String serverUuid, WebhookDto webhook) {
        String topic =
                WebSocketDestinations.Topics.GAME_SERVER_WEBHOOKS.replace("{serverId}", serverUuid);
        var payload = new WebhookEventDto(eventType, serverUuid, webhook);

        log.debug("Publishing {} to {}: {}", eventType, topic, webhook);
        messagingTemplate.convertAndSend(topic, payload);
    }
}
