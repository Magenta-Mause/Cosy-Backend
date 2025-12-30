package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.configs.GamesApiConfig;
import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
import com.magentamause.cosybackend.dtos.gamesapi.GamesApiGamesResponse;
import com.magentamause.cosybackend.exceptions.GamesApiError;
import java.util.Collections;
import java.util.List;
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

    public GamesApiService(GamesApiConfig gamesApiConfig) {
        this.webClient =
                WebClient.builder()
                        .baseUrl(gamesApiConfig.getUrl())
                        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .build();
    }

    public List<GameDto> queryGames(String query) {
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
}
