package com.magentamause.cosybackend.controllers.gameserver.impl;

import com.magentamause.cosybackend.controllers.gameserver.api.GameServerRCONApi;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.RCONConfiguration;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.auth.SecurityContextService;
import com.magentamause.cosybackend.services.core.gameserver.GameServerConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GameServerRCONController implements GameServerRCONApi {
    private final GameServerConfigurationService gameServerConfigurationService;
    private final SecurityContextService securityContextService;

    @Override
    @NeedsValidation(Operation.GAME_SERVER_RCON_CONFIG_CHANGE)
    public ResponseEntity<GameServerDto> updateRconConfiguration(
            @ResourceId String uuid, RCONConfiguration updateDto) {
        GameServerEntity gameServer =
                gameServerConfigurationService.updateRconConfig(uuid, updateDto);
        return ResponseEntity.ok(gameServer.toDto(securityContextService.getUser()));
    }
}
