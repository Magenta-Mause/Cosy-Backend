package com.magentamause.cosybackend.controllers.impl;

import com.google.common.net.HttpHeaders;
import com.magentamause.cosybackend.controllers.api.GamesApi;
import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
import com.magentamause.cosybackend.services.core.games.GamesService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GamesController implements GamesApi {

    private final GamesService gamesService;

    @Override
    public ResponseEntity<List<GameDto>> queryGames(String query) {
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.EXPIRES, "0")
                .body(gamesService.query(query));
    }

    @Override
    public ResponseEntity<GameDto> getGameById(int id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(GameDto.fromEntity(gamesService.getGameEntityByExternalId(id, false)));
    }
}
