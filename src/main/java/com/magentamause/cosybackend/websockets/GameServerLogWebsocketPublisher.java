package com.magentamause.cosybackend.websockets;

import com.magentamause.cosybackend.configs.websockets.WebSocketDestinations;
import com.magentamause.cosybackend.entities.PublicDashboard;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.layout.DashboardTypes;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.services.core.gameserver.GameServerService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerLogWebsocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectProvider<GameServerService> gameServerServiceProvider;

    public void publishLog(String serverUuid, GameServerLogMessageEntity logMessage) {
        String topic =
                WebSocketDestinations.Topics.GAME_SERVER_LOGS.replace("{serverId}", serverUuid);
        messagingTemplate.convertAndSend(topic, logMessage);

        Optional<GameServerEntity> gameServer =
                gameServerServiceProvider.getObject().getOptionalGameServerById(serverUuid);
        if (gameServer.isPresent()
                && gameServer.get().getPublicDashboard() != null
                && gameServer.get().getPublicDashboard().isEnabled()
                && hasLogsLayout(gameServer.get().getPublicDashboard())) {
            String publicTopic =
                    WebSocketDestinations.Topics.PUBLIC_GAME_SERVER_LOGS.replace(
                            "{serverId}", serverUuid);
            messagingTemplate.convertAndSend(publicTopic, logMessage);
        }
    }

    private boolean hasLogsLayout(PublicDashboard publicDashboard) {
        return publicDashboard.getLayouts() != null
                && publicDashboard.getLayouts().stream()
                        .anyMatch(
                                layout ->
                                        layout.getLayoutType().equals(DashboardTypes.LOGS));
    }
}
