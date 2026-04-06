package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.configs.properties.McRouterProperties;
import com.magentamause.cosybackend.entities.CosyInstanceSettingsEntity;
import com.magentamause.cosybackend.entities.McRouterConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CosyInstanceSettingsInitializationService {

    private final CosyInstanceSettingsService settingsService;
    private final McRouterProperties mcRouterProperties;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeSettings() {
        log.info("Initializing Cosy instance settings...");

        if (settingsService.isSettingsAlreadyInitialized()) {
            log.info("Cosy instance settings already exist");
            return;
        }

        CosyInstanceSettingsEntity settings =
                CosyInstanceSettingsEntity.builder()
                        .mcRouterConfiguration(
                                McRouterConfiguration.builder()
                                        .enabled(false)
                                        .port(mcRouterProperties.defaultPort())
                                        .build())
                        .build();

        settingsService.saveSettings(settings);
        log.info("Cosy instance settings initialized successfully");
    }
}
