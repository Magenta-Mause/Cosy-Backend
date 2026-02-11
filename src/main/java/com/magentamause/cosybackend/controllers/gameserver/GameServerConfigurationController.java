package com.magentamause.cosybackend.controllers.gameserver;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.RCONConfiguration;
import com.magentamause.cosybackend.entities.layout.MetricLayout;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.core.gameserver.GameServerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/game-server")
public class GameServerConfigurationController {
    private final GameServerService gameServerService;

    @PatchMapping("{uuid}/layout/metric")
    @NeedsValidation(Operation.GAME_SERVER_UPDATE)
    public ResponseEntity<Void> updateMetricLayout(
            @PathVariable @ResourceId String uuid,
            @Valid @RequestBody List<MetricLayout> metricLayout) {
        gameServerService.updateMetricLayout(uuid, metricLayout);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{uuid}/rcon-configuration")
    @NeedsValidation(Operation.GAME_SERVER_UPDATE)
    public ResponseEntity<GameServerDto> updateRconConfiguration(
            @PathVariable @ResourceId String uuid,
            @RequestBody @Valid RCONConfiguration updateDto) {
        GameServerEntity gameServer = gameServerService.updateRconConfig(uuid, updateDto);
        return ResponseEntity.ok(gameServer.toDto());
    }

}
