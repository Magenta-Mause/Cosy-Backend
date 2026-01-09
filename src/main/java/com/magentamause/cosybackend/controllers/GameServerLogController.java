package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.entities.GameServerLogMessageEntity;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/game-server/{gameServerUuid}/logs")
public class GameServerLogController {

    @GetMapping
    public ResponseEntity<List<GameServerLogMessageEntity>> getLogs(
            @PathVariable String gameServerUuid) {
        // TODO: Replace placeholder empty response with retrieval of log messages for the given
        //       gameServerUuid (e.g. query the log storage or database and return the results).
        return ResponseEntity.ok().body(List.of());
    }
}
