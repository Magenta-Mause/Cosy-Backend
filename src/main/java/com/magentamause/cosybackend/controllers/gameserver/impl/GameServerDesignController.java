package com.magentamause.cosybackend.controllers.gameserver.impl;

import com.magentamause.cosybackend.controllers.gameserver.api.GameServerDesignApi;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.GameServerDesignUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
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
public class GameServerDesignController implements GameServerDesignApi {
    private final GameServerConfigurationService gameServerConfigurationService;
    private final SecurityContextService securityContextService;

    @Override
    @NeedsValidation(Operation.GAME_SERVER_UPDATE)
    public ResponseEntity<GameServerDto> updateDesign(
            @ResourceId String uuid,
            GameServerDesignUpdateDto updateDto) {
        GameServerEntity gameServer =
                gameServerConfigurationService.updateDesign(uuid, updateDto.getDesign());
        return ResponseEntity.ok(gameServer.toDto(securityContextService.getUser()));
    }
}
