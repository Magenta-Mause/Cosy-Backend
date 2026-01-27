package com.magentamause.cosybackend.websockets;

import com.magentamause.cosybackend.configs.websockets.WebSocketDestinations;
import com.magentamause.cosybackend.dtos.actiondtos.MetricPointDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerMetricsPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public void publishMetrics(String serverUuid, MetricPointDto metric) {
        log.debug("Publishing metrics to websocket for server {}: {}", serverUuid, metric);
        String topic =
                WebSocketDestinations.Topics.GAME_SERVER_METRICS.replace("{serverId}", serverUuid);
        messagingTemplate.convertAndSend(topic, metric);
    }
}
