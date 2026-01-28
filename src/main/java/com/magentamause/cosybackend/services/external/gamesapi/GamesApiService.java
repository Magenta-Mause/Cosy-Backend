package com.magentamause.cosybackend.services.external.gamesapi;

import com.magentamause.cosybackend.configs.properties.GamesApiProperties;
import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
import com.magentamause.cosybackend.dtos.gamesapi.GamesApiFindGameByIdResponse;
import com.magentamause.cosybackend.dtos.gamesapi.GamesApiFindGamesSearchResponse;
import com.magentamause.cosybackend.exceptions.GamesApiError;
import com.magentamause.cosybackend.exceptions.gameapi.GameFetchException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(GamesApiProperties.class)
public class GamesApiService {

    private final WebClient gamesApiWebClient;

    public Mono<List<GameDto>> queryGamesApi(String query, boolean includeAssets) {
        Mono<GamesApiFindGamesSearchResponse> response;
        try {
            response =
                    gamesApiWebClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/games")
                                                    .queryParam("query", query)
                                                    .queryParam("include_hero", includeAssets)
                                                    .queryParam("include_logo", includeAssets)
                                                    .build())
                            .retrieve()
                            .bodyToMono(GamesApiFindGamesSearchResponse.class);
        } catch (WebClientRequestException e) {
            throw new GamesApiError("Failed to connect to Games API", e);
        } catch (RuntimeException e) {
            throw new GamesApiError("Unexpected error while calling Games API", e);
        }
        return response.map(
                res ->
                        res.getData() != null && res.getData().getGames() != null
                                ? res.getData().getGames().stream()
                                        .map(game -> game.toDto())
                                        .toList()
                                : List.of());
    }

    public Mono<GameDto> getByExternalId(int externalId) {
        return gamesApiWebClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/game")
                                        .queryParam("id", externalId)
                                        .queryParam("include_hero", true)
                                        .queryParam("include_logo", true)
                                        .build())
                .retrieve()
                .onStatus(
                        status -> status.value() != 200,
                        response ->
                                response.bodyToMono(String.class)
                                        .defaultIfEmpty("")
                                        .map(
                                                body ->
                                                        new GameFetchException(
                                                                "Games API returned status "
                                                                        + response.statusCode()
                                                                                .value()
                                                                        + " for externalId="
                                                                        + externalId
                                                                        + (body.isBlank()
                                                                                ? ""
                                                                                : ", body="
                                                                                        + body))))
                .bodyToMono(GamesApiFindGameByIdResponse.class)
                .map(response -> response.getData().toDto());
    }
}
