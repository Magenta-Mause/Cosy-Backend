package com.magentamause.cosybackend.services.core.games;

import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
import com.magentamause.cosybackend.dtos.template.ExternalGameDto;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.repositories.GameRepository;
import com.magentamause.cosybackend.repositories.TemplateRepository;
import com.magentamause.cosybackend.services.external.templates.CosyTemplateApiService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sources games from the template-service ({@code GET /v3/games}) and caches them as {@link
 * GameEntity} rows. The legacy live SteamGridDB {@code GamesApiService} is no longer used by this
 * (new-backend) path; search and external-id lookup are served entirely from the local cache.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GamesService {

    private final GameRepository gameRepository;
    private final TemplateRepository templateRepository;
    private final CosyTemplateApiService cosyTemplateApiService;

    public List<GameEntity> getAllGames() {
        return gameRepository.findAll();
    }

    public Optional<GameEntity> getGameById(String uuid) {
        return gameRepository.findById(uuid);
    }

    /**
     * Refreshes the local games cache from the template-service {@code /v3/games} endpoint.
     * Existing rows are upserted (matched by slug, falling back to external id) so the same {@link
     * GameEntity} uuid is preserved across refreshes.
     */
    public List<GameEntity> refreshGames() {
        List<ExternalGameDto> games = cosyTemplateApiService.queryCosyGamesApi().block();
        if (games == null) {
            log.warn("Failed to fetch games from Cosy Template API (/v3/games)");
            return gameRepository.findAll();
        }
        for (ExternalGameDto game : games) {
            upsertFromExternal(game);
        }
        return gameRepository.findAll();
    }

    private void upsertFromExternal(ExternalGameDto game) {
        Optional<GameEntity> existing = Optional.empty();
        if (game.slug() != null && !game.slug().isBlank()) {
            existing = gameRepository.findBySlug(game.slug());
        }
        if (existing.isEmpty() && game.externalGameId() != null) {
            existing = gameRepository.findByExternalGameId(game.externalGameId());
        }
        GameEntity entity = existing.orElseGet(GameEntity::new);
        entity.setName(game.name());
        entity.setSlug(game.slug());
        entity.setLogoUrl(game.logoUrl());
        entity.setHeroUrl(game.heroUrl());
        entity.setExternalGameId(game.externalGameId() != null ? game.externalGameId() : 0);
        gameRepository.save(entity);
    }

    /**
     * Local search over the cached known games (no live SGDB call). Only games referenced by at
     * least one template are returned, mirroring the previous behaviour. A blank query returns all
     * known games.
     */
    public List<GameDto> query(String query) {
        Set<String> templateGameIds = templateRepository.findDistinctGameIds();
        List<GameEntity> games =
                (query == null || query.isBlank())
                        ? gameRepository.findAll()
                        : gameRepository.queryByName(query);
        return games.stream()
                .filter(g -> isReferencedByTemplate(g, templateGameIds))
                .map(GameDto::fromEntity)
                .toList();
    }

    private boolean isReferencedByTemplate(GameEntity game, Set<String> templateGameIds) {
        if (game.getSlug() != null && templateGameIds.contains(game.getSlug())) {
            return true;
        }
        return templateGameIds.contains(Integer.toString(game.getExternalGameId()));
    }

    /**
     * Resolves a template's raw {@code game_id} (slug or numeric-as-string) to a cached {@link
     * GameEntity}. Tries slug first; if not found and the value is numeric, falls back to matching
     * {@code externalGameId}. Returns empty if neither matches.
     */
    public Optional<GameEntity> resolveGameForTemplate(String gameId) {
        if (gameId == null || gameId.isBlank()) {
            return Optional.empty();
        }
        Optional<GameEntity> bySlug = gameRepository.findBySlug(gameId);
        if (bySlug.isPresent()) {
            return bySlug;
        }
        try {
            int externalId = Integer.parseInt(gameId.trim());
            return gameRepository.findByExternalGameId(externalId);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public Optional<GameEntity> getOptionalGameByExternalId(int externalId) {
        return gameRepository.findByExternalGameId(externalId);
    }

    public Optional<GameEntity> getOptionalGameByExternalId(Integer externalId) {
        if (externalId == null) {
            return Optional.empty();
        }
        return gameRepository.findByExternalGameId(externalId);
    }

    public GameEntity getGameEntityByExternalId(Integer externalId, boolean storeInDb) {
        if (externalId == null) {
            return null;
        }
        return getGameEntityByExternalId(externalId.intValue(), storeInDb);
    }

    /**
     * Looks up a game by its external (SteamGridDB) id in the local cache. {@code storeInDb} is
     * kept for call-site compatibility but is now a no-op: games are populated exclusively via
     * {@link #refreshGames()} from the template-service, never fetched live.
     */
    public GameEntity getGameEntityByExternalId(int externalId, boolean storeInDb) {
        return gameRepository
                .findByExternalGameId(externalId)
                .orElseThrow(
                        () -> {
                            log.warn("No cached game with externalId: {}", externalId);
                            return new ResponseStatusException(
                                    HttpStatus.NOT_FOUND, "Game not found");
                        });
    }
}
