package com.magentamause.cosybackend.controllers.gameserver;

import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.services.auth.SecurityContextService;
import com.magentamause.cosybackend.services.core.gameserver.GameServerConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/game-server")
public class GameServerPermissionsController {

    private final GameServerConfigurationService gameServerConfigurationService;
    private final SecurityContextService securityContextService;

    @GetMapping("/{uuid}/permissions")
    public ResponseEntity<List<GameServerAccessPermission>> getUserPermissions(@PathVariable String uuid) {
        String currentUserUuid = securityContextService.getUserId();
        return ResponseEntity.ok(gameServerConfigurationService.getUserPermissions(uuid, currentUserUuid));
    }
}
