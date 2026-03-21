package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.dtos.actiondtos.CosyInstanceSettingsUpdateDto;
import com.magentamause.cosybackend.dtos.actiondtos.McRouterConfigurationUpdateDto;
import com.magentamause.cosybackend.entities.CosyInstanceSettingsEntity;
import com.magentamause.cosybackend.entities.McRouterConfiguration;
import com.magentamause.cosybackend.repositories.CosyInstanceSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CosyInstanceSettingsService {

    private final CosyInstanceSettingsRepository repository;

    public CosyInstanceSettingsEntity getSettings() {
        return repository
                .findFirstByOrderByIdAsc()
                .orElseThrow(() -> new RuntimeException("Cosy instance settings not found"));
    }

    public McRouterConfiguration getMcRouterConfiguration() {
        CosyInstanceSettingsEntity settings = getSettings();
        return settings.getMcRouterConfiguration() != null
                ? settings.getMcRouterConfiguration()
                : new McRouterConfiguration();
    }

    @Transactional
    public CosyInstanceSettingsEntity updateSettings(CosyInstanceSettingsUpdateDto updateDto) {
        CosyInstanceSettingsEntity settings = getSettings();
        CosyInstanceSettingsEntity updatedSettings = updateDto.applyToEntity(settings);
        return repository.save(updatedSettings);
    }

    @Transactional
    public McRouterConfiguration updateMcRouterConfiguration(
            McRouterConfigurationUpdateDto updateDto) {
        CosyInstanceSettingsEntity settings = getSettings();
        McRouterConfiguration updatedConfig =
                updateDto.applyToEntity(settings.getMcRouterConfiguration());
        settings.setMcRouterConfiguration(updatedConfig);
        repository.save(settings);
        return updatedConfig;
    }

    @Transactional
    public void saveSettings(CosyInstanceSettingsEntity settings) {
        repository.save(settings);
    }

    public boolean isSettingsAlreadyInitialized() {
        return repository.count() > 0;
    }

    public boolean isMcRouterEnabled() {
        try {
            McRouterConfiguration config = getMcRouterConfiguration();
            return config != null && config.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }
}
