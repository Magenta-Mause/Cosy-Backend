package com.magentamause.cosybackend.controllers.gameserver.configurations;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.RCONConfiguration;
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
public class GameServerRCONController {
    private final GameServerConfigurationService gameServerConfigurationService;
    private final SecurityContextService securityContextService;

    @PatchMapping("/{uuid}/rcon-configuration")
    @NeedsValidation(Operation.GAME_SERVER_RCON_CONFIG_CHANGE)
    public ResponseEntity<GameServerDto> updateRconConfiguration(
            @PathVariable @ResourceId String uuid,
            @RequestBody @Valid RCONConfiguration updateDto) {
        GameServerEntity gameServer =
                gameServerConfigurationService.updateRconConfig(uuid, updateDto);
        return ResponseEntity.ok(gameServer.toDto(securityContextService.getUser()));
    }
}
