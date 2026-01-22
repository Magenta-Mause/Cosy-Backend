package com.magentamause.cosybackend.services.core.games;

import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.exceptions.gameapi.GameFetchException;
import com.magentamause.cosybackend.repositories.GameRepository;
import com.magentamause.cosybackend.services.external.gamesapi.GamesApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GamesService {

    private final GameRepository gameRepository;
    private final GamesApiService gamesApiService;

    public List<GameEntity> getAllGames() {
        return gameRepository.findAll();
    }

    public Optional<GameEntity> getGameById(String uuid) {
        return gameRepository.findById(uuid);
    }

    public Mono<List<GameDto>> query(String query) {
        if (query == null || query.isBlank()) {
            return Mono.just(gameRepository.findAll().stream().map(GameDto::fromEntity).toList());
        }
        return gamesApiService.queryGamesApi(query, false)
                .publishOn(Schedulers.boundedElastic())
                .map(apiGames -> {
                    List<GameEntity> dbGames = gameRepository.queryByName(query);

                    List<GameDto> dbDtos = dbGames.stream()
                            .map(GameDto::fromEntity)
                            .toList();

                    Map<Integer, GameDto> apiGameMap = apiGames.stream()
                            .collect(Collectors.toMap(
                                    GameDto::getExternalGameId,
                                    Function.identity(),
                                    (a, b) -> a
                            ));

                    // DB games first, overriding API if present
                    List<GameDto> result = new ArrayList<>(dbDtos);

                    // Add remaining API games not present in DB
                    apiGames.stream()
                            .filter(apiGame -> !apiGameMap.containsKey(apiGame.getExternalGameId())
                                    || dbGames.stream().noneMatch(
                                    db -> db.getExternalGameId() == apiGame.getExternalGameId()
                            ))
                            .forEach(result::add);

                    return result;
                })
                .onErrorResume(ex ->
                        Mono.fromCallable(() ->
                                gameRepository.queryByName(query).stream()
                                        .map(GameDto::fromEntity)
                                        .toList()
                        ).subscribeOn(Schedulers.boundedElastic())
                );
    }


    public GameEntity getGameEntityByExternalId(int externalId, boolean storeInDb) {
        Optional<GameEntity> game = gameRepository.findByExternalGameId(externalId);
        if (game.isPresent()) {
            return game.get();
        }
        try {
            GameDto fetchedGame = gamesApiService.getByExternalId(externalId)
                    .block();
            if (fetchedGame == null) {
                throw new GameFetchException("Game not found");
            }
            if (!storeInDb) {
                return GameEntity.fromDto(fetchedGame);
            }
            return gameRepository.save(GameEntity.fromDto(fetchedGame));
        } catch (GameFetchException e) {
            log.warn("Could not fetch game with externalId: {}", externalId, e);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found");
        }
    }
}
