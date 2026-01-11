package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.entities.GameServerLogMessageEntity;
import java.util.List;

import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.RequireAccess;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/game-server/{gameServerUuid}/logs")
public class GameServerLogController {

    @GetMapping
    @RequireAccess(action = Action.READ, resource = Resource.GAME_SERVER_LOG)
    public ResponseEntity<List<GameServerLogMessageEntity>> getLogs(
            @ResourceId @PathVariable String gameServerUuid) {
        // TODO: Replace placeholder empty response with retrieval of log messages for the given
        //       gameServerUuid (e.g. query the log storage or database and return the results).
        return ResponseEntity.ok().body(List.of());
    }
}
