package com.magentamause.cosybackend.websockets;

import com.magentamause.cosybackend.configs.websockets.WebSocketDestinations;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.services.user.UserEntityService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerUpdatePublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserEntityService userEntityService;
    private final SimpUserRegistry simpUserRegistry;

    public void publishGameServerUpdate(GameServerEntity gameServer) {
        String topic =
                WebSocketDestinations.Topics.GAME_SERVER_UPDATES.replace(
                        "{serverId}", gameServer.getUuid());

        Map<String, UserEntity> recipients = collectRecipients(gameServer);

        for (UserEntity recipient : recipients.values()) {
            if (simpUserRegistry.getUser(recipient.getUuid()) == null) {
                continue;
            }
            log.debug(
                    "Publishing game server update to user {} for server {}",
                    recipient.getUuid(),
                    gameServer.getUuid());
            messagingTemplate.convertAndSendToUser(
                    recipient.getUuid(), topic, gameServer.toDto(recipient));
        }

        if (gameServer.getPublicDashboard() != null
                && gameServer.getPublicDashboard().isEnabled()) {
            String publicTopic =
                    WebSocketDestinations.Topics.PUBLIC_GAME_SERVER_UPDATES.replace(
                            "{serverId}", gameServer.getUuid());
            messagingTemplate.convertAndSend(publicTopic, gameServer.toPublicDto());
        }
    }

    private Map<String, UserEntity> collectRecipients(GameServerEntity gameServer) {
        Map<String, UserEntity> recipients = new LinkedHashMap<>();

        if (gameServer.getOwner() != null) {
            recipients.put(gameServer.getOwner().getUuid(), gameServer.getOwner());
        }

        if (gameServer.getAccessGroups() != null) {
            for (var group : gameServer.getAccessGroups()) {
                if (group.getUsers() != null) {
                    for (var user : group.getUsers()) {
                        recipients.put(user.getUuid(), user);
                    }
                }
            }
        }

        userEntityService.getAdminUsers().forEach(admin -> recipients.put(admin.getUuid(), admin));

        return recipients;
    }
}
