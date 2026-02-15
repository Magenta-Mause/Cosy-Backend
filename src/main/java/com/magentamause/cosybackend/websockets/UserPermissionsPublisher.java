package com.magentamause.cosybackend.websockets;

import com.magentamause.cosybackend.configs.websockets.WebSocketDestinations;
import com.magentamause.cosybackend.dtos.websockets.GameServerPermissionChangeDto;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPermissionsPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishPermissionUpdate(
            String userId, String serverId, List<GameServerAccessPermission> newPermissions) {
        String topic =
                WebSocketDestinations.Topics.GAME_SERVER_PERMISSIONS_CONFIG_CHANGE.replace(
                        "{userId}", userId);
        GameServerPermissionChangeDto payload =
                GameServerPermissionChangeDto.builder()
                        .gameServerUuid(serverId)
                        .permissions(newPermissions)
                        .build();

        log.info(
                "Publishing permission update [user: {}, server: {}, permissions: {}]",
                userId,
                serverId,
                newPermissions);
        messagingTemplate.convertAndSend(topic, payload);
    }
}
