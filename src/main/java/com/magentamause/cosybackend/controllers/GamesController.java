package com.magentamause.cosybackend.controllers;

import com.google.common.net.HttpHeaders;
import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
import com.magentamause.cosybackend.services.core.games.GameService;
import jakarta.validation.constraints.NotBlank;
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

    private final GameService gameService;

    @GetMapping
    public ResponseEntity<Mono<List<GameDto>>> queryGames(@RequestParam @NotBlank String query) {
        return ResponseEntity.
                status(HttpStatus.OK)
                .header(HttpHeaders.EXPIRES, "0")
                .body(gameService.query(query));
    }

    @GetMapping("/external/{id}")
    public ResponseEntity<GameDto> getGameById(@PathVariable int id) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(GameDto.fromEntity(gameService.getGameEntityByExternalId(id, false)));
    }
}
