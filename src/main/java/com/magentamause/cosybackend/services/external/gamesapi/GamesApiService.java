package com.magentamause.cosybackend.services.external.gamesapi;

import com.magentamause.cosybackend.configs.GamesApiProperties;
import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
import com.magentamause.cosybackend.dtos.gamesapi.GamesApiGamesResponse;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.exceptions.GamesApiError;
import com.magentamause.cosybackend.repositories.GameRepository;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(GamesApiProperties.class)
public class GamesApiService {

    private final WebClient gamesApiWebClient;
    private final GameRepository gameRepository;

    private List<GameDto> queryGamesApi(String query) {
        GamesApiGamesResponse response;
        try {
            response =
                    gamesApiWebClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/games")
                                                    .queryParam("query", query)
                                                    .queryParam("include_hero", "true")
                                                    .queryParam("include_logo", "true")
                                                    .build())
                            .retrieve()
                            .bodyToMono(GamesApiGamesResponse.class)
                            .block();
        } catch (WebClientRequestException e) {
            throw new GamesApiError("Failed to connect to Games API", e);
        } catch (RuntimeException e) {
            throw new GamesApiError("Unexpected error while calling Games API", e);
        }

        if (response == null
                || response.getData() == null
                || response.getData().getGames() == null) {
            return Collections.emptyList();
        }

        return response.getData().getGames().stream()
                .map(GamesApiGamesResponse.DataPayload.GamesPayload::toDto)
                .toList();
    }

    public List<GameDto> query(String query) {
        List<GameDto> apiGames;

        try {
            apiGames = queryGamesApi(query);
        } catch (GamesApiError e) {
            log.warn("Games API query failed, falling back to cached results");
            List<GameDto> localGamesStream =
                    gameRepository.findByNameContainingIgnoreCase(query).stream()
                            .map(GameDto::fromEntity)
                            .toList();
            if (localGamesStream.isEmpty()) {
                throw e;
            }
            return localGamesStream;
        }

        return apiGames.stream()
                .map(
                        g ->
                                GameDto.fromEntity(
                                        gameRepository.saveIfNotPresent(GameEntity.fromDto(g))))
                .toList();
    }
}
