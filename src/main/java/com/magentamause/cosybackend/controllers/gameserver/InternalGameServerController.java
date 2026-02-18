package com.magentamause.cosybackend.controllers.gameserver;

import com.magentamause.cosybackend.services.core.gameserver.GameServerService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/game-server")
public class InternalGameServerController {

    private final GameServerService gameServerService;

    @PutMapping("/custom-metric/{uuid}")
    public ResponseEntity<Map<String, Object>> updateCustomMetric(
            @PathVariable String uuid,
            @RequestHeader("Authorization") String secret,
            @RequestBody Map<String, Object> value) {
        return ResponseEntity.ok(gameServerService.updateCustomMetric(uuid, secret, value));
    }

    @GetMapping("/test-connection/{uuid}")
    public ResponseEntity<Boolean> checkConnection(
            @PathVariable String uuid, @RequestHeader("Authorization") String secret) {
        return ResponseEntity.ok(gameServerService.checkGameServerConnection(uuid, secret));
    }
}
