package com.magentamause.cosybackend.controllers.gameserver.impl;

import com.magentamause.cosybackend.controllers.gameserver.api.GameServerPermissionsApi;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.services.auth.SecurityContextService;
import com.magentamause.cosybackend.services.core.gameserver.GameServerAccessGroupService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GameServerPermissionsController implements GameServerPermissionsApi {

    private final GameServerAccessGroupService gameServerAccessGroupService;
    private final SecurityContextService securityContextService;

    @Override
    public ResponseEntity<List<GameServerAccessPermission>> getUserPermissions(
            String uuid) {
        String currentUserUuid = securityContextService.getUserId();
        return ResponseEntity.ok(
                gameServerAccessGroupService.getUserPermissions(uuid, currentUserUuid));
    }
}
