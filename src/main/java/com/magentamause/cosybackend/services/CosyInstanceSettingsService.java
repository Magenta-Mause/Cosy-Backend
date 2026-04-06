package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.dtos.actiondtos.CosyInstanceSettingsUpdateDto;
import com.magentamause.cosybackend.dtos.actiondtos.McRouterConfigurationUpdateDto;
import com.magentamause.cosybackend.entities.CosyInstanceSettingsEntity;
import com.magentamause.cosybackend.entities.McRouterConfiguration;
import com.magentamause.cosybackend.exceptions.McRouterException;
import com.magentamause.cosybackend.repositories.CosyInstanceSettingsRepository;
import com.magentamause.cosybackend.services.engine.docker.McRouterContainerService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class CosyInstanceSettingsService {

    private final CosyInstanceSettingsRepository repository;
    private final McRouterContainerService mcRouterContainerService;

    public CosyInstanceSettingsService(
            CosyInstanceSettingsRepository repository,
            @Lazy McRouterContainerService mcRouterContainerService) {
        this.repository = repository;
        this.mcRouterContainerService = mcRouterContainerService;
    }

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

    /**
     * Updates MC-Router configuration with auto-start/stop and rollback on failure.
     *
     * @throws McRouterException if auto-start fails (config is rolled back)
     */
    public McRouterConfiguration updateMcRouterConfigurationWithLifecycle(
            McRouterConfigurationUpdateDto updateDto) throws McRouterException {
        // Snapshot current values before update (the object may be mutated in-place by JPA)
        McRouterConfiguration currentConfig = getMcRouterConfiguration();
        boolean previousEnabled = currentConfig.isEnabled();
        int previousPort = currentConfig.getPort();
        List<String> previousDomains =
                currentConfig.getDomains() != null
                        ? List.copyOf(currentConfig.getDomains())
                        : List.of();

        boolean willBeEnabled =
                updateDto.getEnabled() != null ? updateDto.getEnabled() : previousEnabled;

        McRouterConfiguration updatedConfig = updateMcRouterConfiguration(updateDto);

        // Auto-stop when disabling
        if (previousEnabled && !willBeEnabled) {
            mcRouterContainerService.removeMcRouterContainer();
        }

        // Auto-start when enabling if running servers with domains exist
        if (!previousEnabled && willBeEnabled) {
            try {
                mcRouterContainerService.ensureMcRouterRunningIfNeeded();
            } catch (McRouterException e) {
                log.warn("Failed to auto-start MC-Router after enabling: {}", e.getMessage());
                try {
                    McRouterConfigurationUpdateDto rollbackDto =
                            new McRouterConfigurationUpdateDto();
                    rollbackDto.setEnabled(previousEnabled);
                    rollbackDto.setPort(previousPort);
                    rollbackDto.setDomains(previousDomains);
                    updateMcRouterConfiguration(rollbackDto);
                } catch (Exception rollbackEx) {
                    log.error(
                            "Failed to rollback MC-Router configuration after auto-start failure",
                            rollbackEx);
                }
                throw e;
            }
        }

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
        } catch (RuntimeException e) {
            log.warn("Failed to determine if McRouter is enabled; defaulting to disabled", e);
            return false;
        }
    }
}
