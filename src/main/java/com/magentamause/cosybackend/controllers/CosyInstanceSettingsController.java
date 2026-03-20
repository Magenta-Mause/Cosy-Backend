package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.dtos.actiondtos.CosyInstanceSettingsUpdateDto;
import com.magentamause.cosybackend.dtos.actiondtos.McRouterConfigurationUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.CosyInstanceSettingsDto;
import com.magentamause.cosybackend.dtos.entitydtos.McRouterConfigurationDto;
import com.magentamause.cosybackend.dtos.entitydtos.McRouterStatusDto;
import com.magentamause.cosybackend.entities.CosyInstanceSettingsEntity;
import com.magentamause.cosybackend.entities.McRouterConfiguration;
import com.magentamause.cosybackend.exceptions.McRouterException;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.services.CosyInstanceSettingsService;
import com.magentamause.cosybackend.services.engine.docker.McRouterContainerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("cosy-settings")
@Slf4j
public class CosyInstanceSettingsController {

    private final CosyInstanceSettingsService settingsService;
    private final McRouterContainerService mcRouterContainerService;

    @GetMapping
    @NeedsValidation(Operation.COSY_SETTINGS_READ)
    public ResponseEntity<CosyInstanceSettingsDto> getSettings() {
        CosyInstanceSettingsEntity settings = settingsService.getSettings();
        return ResponseEntity.ok(settings.toDto());
    }

    @PutMapping
    @NeedsValidation(Operation.COSY_SETTINGS_UPDATE)
    public ResponseEntity<CosyInstanceSettingsDto> updateSettings(
            @Valid @RequestBody CosyInstanceSettingsUpdateDto updateDto) {
        log.info("Updating Cosy instance settings");
        CosyInstanceSettingsEntity updatedSettings = settingsService.updateSettings(updateDto);
        return ResponseEntity.ok(updatedSettings.toDto());
    }

    @GetMapping("/mc-router")
    public ResponseEntity<McRouterConfigurationDto> getMcRouterConfiguration() {
        // Public to any authenticated user - needed for frontend to check if enabled
        McRouterConfiguration config = settingsService.getMcRouterConfiguration();
        return ResponseEntity.ok(config.toDto());
    }

    @PutMapping("/mc-router")
    @NeedsValidation(Operation.COSY_SETTINGS_UPDATE)
    public ResponseEntity<McRouterConfigurationDto> updateMcRouterConfiguration(
            @Valid @RequestBody McRouterConfigurationUpdateDto updateDto) {
        log.info("Updating MC-Router configuration");

        McRouterConfiguration currentConfig = settingsService.getMcRouterConfiguration();
        boolean wasEnabled = currentConfig.isEnabled();
        boolean willBeEnabled =
                updateDto.getEnabled() != null ? updateDto.getEnabled() : wasEnabled;

        McRouterConfiguration updatedConfig =
                settingsService.updateMcRouterConfiguration(updateDto);

        // Auto-stop when disabling
        if (wasEnabled && !willBeEnabled) {
            mcRouterContainerService.removeMcRouterContainer();
        }

        // Auto-start when enabling if running servers with domains exist
        if (!wasEnabled && willBeEnabled) {
            try {
                mcRouterContainerService.ensureMcRouterRunningIfNeeded();
            } catch (McRouterException e) {
                log.warn("Failed to auto-start MC-Router after enabling: {}", e.getMessage());
                throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
            }
        }

        return ResponseEntity.ok(updatedConfig.toDto());
    }

    @GetMapping("/mc-router/status")
    @NeedsValidation(Operation.COSY_SETTINGS_READ)
    public ResponseEntity<McRouterStatusDto> getMcRouterStatus() {
        McRouterStatusDto status = mcRouterContainerService.getStatus();
        return ResponseEntity.ok(status);
    }
}
