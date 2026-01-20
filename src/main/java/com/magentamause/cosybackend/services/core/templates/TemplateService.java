package com.magentamause.cosybackend.services.core.templates;

import com.magentamause.cosybackend.dtos.template.TemplateDto;
import com.magentamause.cosybackend.entities.TemplateEntity;
import com.magentamause.cosybackend.repositories.TemplateRepository;
import com.magentamause.cosybackend.services.external.templates.CosyTemplateApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {
    private final CosyTemplateApiService cosyTemplateApiService;
    private final TemplateRepository templateRepository;

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public List<TemplateEntity> refreshTemplates() {
        log.info("Refreshing templates...");
        try {
            List<TemplateDto> templates = cosyTemplateApiService.queryCosyTemplateApi().block();
            templateRepository.deleteAll();
            assert templates != null;
            return templateRepository.saveAll(templates.stream().map(TemplateEntity::ofDto).toList());
        } catch (Exception e) {
            log.error("Failed to refresh templates: {}", e.getMessage(), e);
            throw e;
        }
    }

    public List<TemplateEntity> getAllTemplates() {
        return templateRepository.findAll();
    }

}
