package com.magentamause.cosybackend.controllers.gameserver;

import com.magentamause.cosybackend.services.core.gameserver.GameServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/game-server")
public class InternalGameServerController {

    private final GameServerService gameServerService;

    @PutMapping("/custom-metric/{uuid}/{secret}")
    public ResponseEntity<Map<String, Object>> updateCustomMetric(@PathVariable String uuid, @PathVariable String secret, @RequestBody Map<String, Object> value) {
        log.info("Updating custom metric");
        return ResponseEntity.ok(gameServerService.updateCustomMetric(uuid, secret, value));
    }
}
