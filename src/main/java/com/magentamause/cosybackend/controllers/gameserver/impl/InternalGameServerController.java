package com.magentamause.cosybackend.controllers.gameserver.impl;

import com.magentamause.cosybackend.controllers.gameserver.api.InternalGameServerApi;
import com.magentamause.cosybackend.services.core.gameserver.GameServerService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class InternalGameServerController implements InternalGameServerApi {

    private final GameServerService gameServerService;

    @Override
    public ResponseEntity<Map<String, Object>> updateCustomMetric(
            String uuid,
            String secret,
            Map<String, Object> value) {
        return ResponseEntity.ok(gameServerService.updateCustomMetric(uuid, secret, value));
    }

    @Override
    public ResponseEntity<Boolean> checkConnection(
            String uuid, String secret) {
        return ResponseEntity.ok(gameServerService.checkGameServerConnection(uuid, secret));
    }
}
