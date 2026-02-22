package com.magentamause.cosybackend.controllers.gameserver.configurations;

import com.magentamause.cosybackend.dtos.actiondtos.gameserver.GameServerDesignUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.auth.SecurityContextService;
import com.magentamause.cosybackend.services.core.gameserver.GameServerConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/game-server")
public class GameServerDesignController {
    private final GameServerConfigurationService gameServerConfigurationService;
    private final SecurityContextService securityContextService;

    @PatchMapping("/{uuid}/design")
    @NeedsValidation(Operation.GAME_SERVER_UPDATE)
    public ResponseEntity<GameServerDto> updateDesign(
            @PathVariable @ResourceId String uuid,
            @RequestBody @Valid GameServerDesignUpdateDto updateDto) {
        GameServerEntity gameServer =
                gameServerConfigurationService.updateDesign(uuid, updateDto.getDesign());
        return ResponseEntity.ok(gameServer.toDto(securityContextService.getUser()));
    }
}
