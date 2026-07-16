package com.magentamause.cosybackend.services.core.templates;

import com.magentamause.cosybackend.dtos.template.ExternalTemplateDto;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.entities.TemplateEntity;
import com.magentamause.cosybackend.repositories.TemplateRepository;
import com.magentamause.cosybackend.services.core.games.GamesService;
import com.magentamause.cosybackend.services.external.templates.CosyTemplateApiService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {
    private final CosyTemplateApiService cosyTemplateApiService;
    private final TemplateRepository templateRepository;
    private final GamesService gamesService;
    private final TransactionTemplate transactionTemplate;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public List<TemplateEntity> refreshTemplates() {
        log.info("Refreshing templates...");
        try {
            // Refresh the games cache first so template game_id resolution sees the latest index.
            gamesService.refreshGames();

            List<ExternalTemplateDto> templates =
                    cosyTemplateApiService.queryCosyTemplateApi().block();
            if (templates == null || templates.isEmpty()) {
                log.warn("Failed to fetch templates from Cosy Template API");
                throw new RuntimeException("Failed to fetch templates from Cosy Template API");
            }
            for (ExternalTemplateDto template : templates) {
                log.info("Found template: {}", template.name());
                // game_id is a slug or numeric-as-string; resolve with numeric fallback.
                Optional<GameEntity> game = gamesService.resolveGameForTemplate(template.gameId());
                if (game.isPresent()) {
                    log.info(
                            "Resolved template '{}' game_id '{}' -> {}",
                            template.name(),
                            template.gameId(),
                            game.get().getName());
                } else {
                    log.warn(
                            "Could not resolve game for template '{}' (game_id '{}')",
                            template.name(),
                            template.gameId());
                }
            }
            // Delete + insert must be one transaction: a failed insert must not leave the catalog
            // wiped.
            List<TemplateEntity> saved =
                    transactionTemplate.execute(
                            status -> {
                                templateRepository.deleteAll();
                                return templateRepository.saveAll(
                                        templates.stream().map(TemplateEntity::ofDto).toList());
                            });
            isInitialized.set(true);
            return saved;
        } catch (Exception e) {
            log.error("Failed to refresh templates: {}", e.getMessage(), e);
            return null;
        }
    }

    public List<TemplateEntity> getAllTemplates() {
        if (!isInitialized.get()) {
            refreshTemplates();
        }
        return templateRepository.findAll();
    }
}
