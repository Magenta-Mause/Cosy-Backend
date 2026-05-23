package com.magentamause.cosybackend.services.core.games;

import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.exceptions.gameapi.GameFetchException;
import com.magentamause.cosybackend.repositories.GameRepository;
import com.magentamause.cosybackend.repositories.TemplateRepository;
import com.magentamause.cosybackend.services.external.gamesapi.GamesApiService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class GamesService {

    private final GameRepository gameRepository;
    private final TemplateRepository templateRepository;
    private final GamesApiService gamesApiService;

    public List<GameEntity> getAllGames() {
        return gameRepository.findAll();
    }

    public Optional<GameEntity> getGameById(String uuid) {
        return gameRepository.findById(uuid);
    }

    public Mono<List<GameDto>> query(String query) {
        if (query == null || query.isBlank()) {
            return Mono.just(queryLocalGames(null));
        }
        return gamesApiService
                .queryGamesApi(query, false)
                .publishOn(Schedulers.boundedElastic())
                .map(apiGames -> mergeWithLocalGames(query, apiGames))
                .onErrorResume(
                        ex ->
                                Mono.fromCallable(() -> queryLocalGames(query))
                                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private List<GameDto> queryLocalGames(String query) {
        Set<Integer> templateGameIds = templateRepository.findDistinctExternalGameIds();
        List<GameEntity> games;
        if (query == null) {
            games = gameRepository.findByExternalGameIdIn(new ArrayList<>(templateGameIds));
        } else {
            games =
                    gameRepository.queryByName(query).stream()
                            .filter(g -> templateGameIds.contains(g.getExternalGameId()))
                            .toList();
        }
        return games.stream().map(GameDto::fromEntity).toList();
    }

    private List<GameDto> mergeWithLocalGames(String query, List<GameDto> apiGames) {
        Set<Integer> templateGameIds = templateRepository.findDistinctExternalGameIds();
        Set<Integer> dbExternalIds = new java.util.HashSet<>();
        List<GameEntity> dbGames =
                gameRepository.queryByName(query).stream()
                        .filter(g -> templateGameIds.contains(g.getExternalGameId()))
                        .peek(g -> dbExternalIds.add(g.getExternalGameId()))
                        .toList();
        List<GameDto> result = new ArrayList<>(dbGames.stream().map(GameDto::fromEntity).toList());

        apiGames.stream()
                .filter(apiGame -> templateGameIds.contains(apiGame.getExternalGameId()))
                .filter(apiGame -> !dbExternalIds.contains(apiGame.getExternalGameId()))
                .forEach(result::add);

        return result;
    }

    public Optional<GameEntity> getOptionalGameByExternalId(int externalId, boolean storeInDb) {
        try {
            return Optional.of(getGameEntityByExternalId(externalId, storeInDb));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public Optional<GameEntity> getOptionalGameByExternalId(Integer externalId, boolean storeInDb) {
        if (externalId == null) {
            return Optional.empty();
        }
        return getOptionalGameByExternalId(externalId.intValue(), storeInDb);
    }

    public GameEntity getGameEntityByExternalId(Integer externalId, boolean storeInDb) {
        if (externalId == null) {
            return null;
        }
        return getGameEntityByExternalId(externalId.intValue(), storeInDb);
    }

    public GameEntity getGameEntityByExternalId(int externalId, boolean storeInDb) {
        Optional<GameEntity> game = gameRepository.findByExternalGameId(externalId);
        if (game.isPresent()) {
            return game.get();
        }
        try {
            GameDto fetchedGame = gamesApiService.getByExternalId(externalId).block();
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
