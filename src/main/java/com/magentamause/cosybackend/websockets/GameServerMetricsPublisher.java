package com.magentamause.cosybackend.websockets;

import com.magentamause.cosybackend.configs.websockets.WebSocketDestinations;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.MetricPointDto;
import com.magentamause.cosybackend.entities.PublicDashboard;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.services.core.gameserver.GameServerService;
import com.magentamause.cosybackend.services.core.metrics.MetricsService;
import com.magentamause.cosybackend.services.core.metrics.MetricsUtilService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerMetricsPublisher {
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectProvider<GameServerService> gameServerServiceProvider;
    private final ObjectProvider<MetricsService> metricsServiceProvider;
    private final MetricsUtilService metricsUtilService;

    public void publishMetrics(String serverUuid, MetricPointDto metric) {
        String topic =
                WebSocketDestinations.Topics.GAME_SERVER_METRICS.replace("{serverId}", serverUuid);
        messagingTemplate.convertAndSend(topic, metric);

        Optional<GameServerEntity> gameServer =
                gameServerServiceProvider.getObject().getOptionalGameServerById(serverUuid);
        if (gameServer.isPresent()) {
            PublicDashboard publicDashboard = gameServer.get().getPublicDashboard();
            if (publicDashboard != null && publicDashboard.isEnabled()) {
                List<String> allowedTypes =
                        metricsServiceProvider.getObject().extractPublicMetrics(publicDashboard);
                if (!allowedTypes.isEmpty()) {
                    List<MetricPointDto> filtered =
                            metricsUtilService.filterMetrics(
                                    List.of(metric), allowedTypes.toArray(String[]::new));
                    if (!filtered.isEmpty()) {
                        String publicTopic =
                                WebSocketDestinations.Topics.PUBLIC_GAME_SERVER_METRICS.replace(
                                        "{serverId}", serverUuid);
                        messagingTemplate.convertAndSend(publicTopic, filtered.getFirst());
                    }
                }
            }
        }
    }
}
