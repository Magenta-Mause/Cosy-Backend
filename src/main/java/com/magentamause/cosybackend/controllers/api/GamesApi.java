package com.magentamause.cosybackend.controllers.api;

import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Games", description = "Game catalog")
@RequestMapping("/games")
public interface GamesApi {

    @Operation(summary = "Query games by name")
    @ApiResponse(responseCode = "200", description = "Games returned")
    @GetMapping
    ResponseEntity<List<GameDto>> queryGames(
            @Parameter(description = "Search query") @RequestParam(required = false) String query);

    @Operation(summary = "Get game by external ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Game found"),
        @ApiResponse(responseCode = "404", description = "Game not found")
    })
    @GetMapping("/external/{id}")
    ResponseEntity<GameDto> getGameById(
            @Parameter(description = "External game ID") @PathVariable int id);
}
