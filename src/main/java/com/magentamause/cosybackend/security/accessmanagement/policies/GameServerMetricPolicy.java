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
public class GameServerMetricPolicy {

    @Validates(Operation.GAME_SERVER_METRIC_READ)
    public static boolean canReadMetrics(
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
                        .anyMatch(layout -> layout.getLayoutType() == DashboardTypes.METRIC)) {
            return true;
        }
        return GameServerPermissionsUtility.isOwnerOrHasPermission(
                gameServerEntity.get(), user, GameServerAccessPermission.READ_SERVER_METRICS);
    }

    @Validates(Operation.GAME_SERVER_METRIC_READ_PUBLIC)
    public static boolean canReadPublicMetrics(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return true;
    }
}
