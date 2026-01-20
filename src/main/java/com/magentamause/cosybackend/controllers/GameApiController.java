package com.magentamause.cosybackend.controllers;

import com.google.common.net.HttpHeaders;
import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
import com.magentamause.cosybackend.services.external.gamesapi.GamesApiService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/games")
public class GameApiController {

    private final GamesApiService gamesApiService;

    @GetMapping
    public ResponseEntity<Mono<List<GameDto>>> getGameInfo(@RequestParam @NotBlank String query) {
        return ResponseEntity.
                status(HttpStatus.OK)
                .header(HttpHeaders.EXPIRES, "0")
                .body(gamesApiService.query(query));
    }
}
