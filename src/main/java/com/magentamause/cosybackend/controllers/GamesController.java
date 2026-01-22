package com.magentamause.cosybackend.controllers;

import com.google.common.net.HttpHeaders;
import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
import com.magentamause.cosybackend.services.core.games.GamesService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/games")
public class GamesController {

    private final GamesService gamesService;

    @GetMapping
    public Mono<ResponseEntity<List<GameDto>>> queryGames(
            @RequestParam(required = false) String query) {
        return gamesService
                .query(query)
                .map(
                        response ->
                                ResponseEntity.status(HttpStatus.OK)
                                        .header(HttpHeaders.EXPIRES, "0")
                                        .body(response));
    }

    @GetMapping("/external/{id}")
    public ResponseEntity<GameDto> getGameById(@PathVariable int id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(GameDto.fromEntity(gamesService.getGameEntityByExternalId(id, false)));
    }
}
