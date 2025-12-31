package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.configs.GamesApiConfig;
import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
import com.magentamause.cosybackend.dtos.gamesapi.GamesApiGamesResponse;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.exceptions.GamesApiError;
import com.magentamause.cosybackend.repositories.GameRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

@Service
@EnableConfigurationProperties(GamesApiConfig.class)
public class GamesApiService {

    private final WebClient webClient;
    private final GameRepository gameRepository;

    public GamesApiService(GamesApiConfig gamesApiConfig, GameRepository gameRepository) {
        this.webClient =
                WebClient.builder()
                        .baseUrl(gamesApiConfig.getUrl())
                        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .build();
        this.gameRepository = gameRepository;
    }

    private List<GameDto> queryGamesApi(String query) {
        GamesApiGamesResponse response;
        try {
            response =
                    webClient
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

        return response.getData().getGames();
    }

    public List<GameDto> queryGames(String query) {
        Stream<GameDto> cachedGamesStream =
                gameRepository.findByNameContainingIgnoreCase(query).stream()
                        .map(GameDto::fromEntity);

        try {
            Stream<GameDto> apiGamesStream = queryGamesApi(query).stream();
            Map<Integer, Boolean> seen = new ConcurrentHashMap<>();
            return Stream.concat(cachedGamesStream, apiGamesStream)
                    .filter(g -> {
                        if (seen.putIfAbsent(g.getId(), Boolean.TRUE) == null) {
                            gameRepository.saveIfNotPresent(GameEntity.fromDto(g));
                            return true;
                        } else {
                            return false;
                        }
                    })
                    .toList();

        } catch (GamesApiError e) {
            List<GameDto> cachedGames = cachedGamesStream.toList();
            if (cachedGames.isEmpty()) {
                throw e;
            }
            return cachedGames;
        }

    }
}
