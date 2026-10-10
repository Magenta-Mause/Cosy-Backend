package com.magentamause.cosybackend.services.core.timerange;

import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.services.auth.GameServerPermissionsUtility;
import com.magentamause.cosybackend.services.auth.SecurityContextService;
import com.magentamause.cosybackend.services.core.gameserver.GameServerService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Resolves requested time ranges for a game server, applying the caller's access limits. */
@Service
@RequiredArgsConstructor
public class TimeRangeService {

    private final TimeRangeResolver timeRangeResolver;
    private final GameServerService gameServerService;
    private final SecurityContextService securityContextService;

    /**
     * Resolves a range for reading data of a game server. Callers without {@code permission} — i.e.
     * visitors reading through the public dashboard — get the public lookback limit.
     */
    public TimeRange resolveForGameServer(
            String gameServerUuid,
            Instant start,
            Instant end,
            GameServerAccessPermission permission) {
        GameServerEntity gameServer = gameServerService.getOrThrow(gameServerUuid);
        boolean restricted =
                !GameServerPermissionsUtility.isOwnerOrHasPermission(
                        gameServer, securityContextService.getUser(), permission);
        return timeRangeResolver.resolve(start, end, restricted);
    }

    /** Resolves a range for an endpoint that is always public. */
    public TimeRange resolvePublic(Instant start, Instant end) {
        return timeRangeResolver.resolve(start, end, true);
    }
}
