package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.PublicDashboard;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.entities.layout.DashboardTypes;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.Validates;
import com.magentamause.cosybackend.services.auth.GameServerPermissionsUtility;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class GameServerLogPolicy {

    @Validates(Operation.GAME_SERVER_LOG_READ)
    public static boolean canReadLogs(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        Optional<GameServerEntity> gameServerEntity =
                resolver.getGameServerEntity((String) referenceId);
        if (gameServerEntity.isEmpty()) {
            return false;
        }
        PublicDashboard publicDashboard = gameServerEntity.get().getPublicDashboard();
        if (publicDashboard != null
                && publicDashboard.isEnabled()
                && publicDashboard.getLayouts().stream()
                        .anyMatch(
                                layout -> layout.getPublicDashboardType() == DashboardTypes.LOGS)) {
            return true;
        }
        return GameServerPermissionsUtility.isOwnerOrHasPermission(
                gameServerEntity.get(), user, GameServerAccessPermission.READ_SERVER_LOGS);
    }
}
