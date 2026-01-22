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
    public ResponseEntity<Mono<List<GameDto>>> queryGames(@RequestParam String query) {
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.EXPIRES, "0")
                .body(gamesService.query(query));
    }

    @GetMapping("/external/{id}")
    public ResponseEntity<GameDto> getGameById(@PathVariable int id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(GameDto.fromEntity(gamesService.getGameEntityByExternalId(id, false)));
    }
}
