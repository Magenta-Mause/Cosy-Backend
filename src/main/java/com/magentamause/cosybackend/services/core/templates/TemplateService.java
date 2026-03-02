package com.magentamause.cosybackend.services.core.templates;

import com.magentamause.cosybackend.dtos.template.ExternalTemplateDto;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.entities.TemplateEntity;
import com.magentamause.cosybackend.repositories.TemplateRepository;
import com.magentamause.cosybackend.services.core.games.GamesService;
import com.magentamause.cosybackend.services.external.templates.CosyTemplateApiService;

import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {
    private final CosyTemplateApiService cosyTemplateApiService;
    private final TemplateRepository templateRepository;
    private final GamesService gamesService;

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public List<TemplateEntity> refreshTemplates() {
        log.info("Refreshing templates...");
        try {
            List<ExternalTemplateDto> templates =
                    cosyTemplateApiService.queryCosyTemplateApi().block();
            if (templates == null || templates.isEmpty()) {
                log.warn("Failed to fetch templates from Cosy Template API");
                throw new RuntimeException("Failed to fetch templates from Cosy Template API");
            }
            templateRepository.deleteAll();
            for (ExternalTemplateDto template : templates) {
                log.info("Found template: {}", template.name());
                log.info("Fetching Game with external id: {}", template.gameId());
                GameEntity game = gamesService.getGameEntityByExternalId(template.gameId(), true);
                log.info("Fetched Game: {}", game.getName());
            }
            return templateRepository.saveAll(
                    templates.stream().map(TemplateEntity::ofDto).toList());
        } catch (Exception e) {
            log.error("Failed to refresh templates: {}", e.getMessage(), e);
            return null;
        }
    }

    public List<TemplateEntity> getAllTemplates() {
        if (templateRepository.count() == 0) {
            refreshTemplates();
        }
        return templateRepository.findAll();
    }
}
