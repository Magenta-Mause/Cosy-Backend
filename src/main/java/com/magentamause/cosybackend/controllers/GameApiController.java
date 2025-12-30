package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.RequireAccess;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import com.magentamause.cosybackend.services.GamesApiService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/games-info")
public class GameApiController {

    private final GamesApiService gamesApiService;

    @GetMapping
    public ResponseEntity<List<GameDto>> getGameInfo(@RequestParam @NotBlank String query) {
        List<GameDto> games = gamesApiService.queryGames(query);
        return ResponseEntity.ok(games);
    }
}
