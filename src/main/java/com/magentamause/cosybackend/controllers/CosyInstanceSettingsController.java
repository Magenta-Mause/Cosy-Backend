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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
            @Valid @RequestBody McRouterConfigurationUpdateDto updateDto,
            @RequestParam(defaultValue = "false") boolean force) {
        log.info("Updating MC-Router configuration, force={}", force);

        McRouterConfiguration currentConfig = settingsService.getMcRouterConfiguration();
        boolean wasEnabled = currentConfig.isEnabled();
        boolean willBeEnabled =
                updateDto.getEnabled() != null ? updateDto.getEnabled() : wasEnabled;

        // Check if disabling while servers are running
        if (wasEnabled && !willBeEnabled) {
            try {
                mcRouterContainerService.stopMcRouter(force);
            } catch (McRouterException e) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        e.getMessage()
                                + ". Use force=true to stop MC-Router anyway (servers will lose domain routing).");
            }
        }

        McRouterConfiguration updatedConfig =
                settingsService.updateMcRouterConfiguration(updateDto);
        return ResponseEntity.ok(updatedConfig.toDto());
    }

    @GetMapping("/mc-router/status")
    @NeedsValidation(Operation.MC_ROUTER_STATUS_READ)
    public ResponseEntity<McRouterStatusDto> getMcRouterStatus() {
        McRouterStatusDto status = mcRouterContainerService.getStatus();
        return ResponseEntity.ok(status);
    }

    @PostMapping("/mc-router/start")
    @NeedsValidation(Operation.COSY_SETTINGS_UPDATE)
    public ResponseEntity<McRouterStatusDto> startMcRouter() {
        log.info("Starting MC-Router container");
        try {
            mcRouterContainerService.startMcRouter();
            return ResponseEntity.ok(mcRouterContainerService.getStatus());
        } catch (McRouterException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/mc-router/stop")
    @NeedsValidation(Operation.COSY_SETTINGS_UPDATE)
    public ResponseEntity<McRouterStatusDto> stopMcRouter(
            @RequestParam(defaultValue = "false") boolean force) {
        log.info("Stopping MC-Router container, force={}", force);
        try {
            mcRouterContainerService.stopMcRouter(force);
            return ResponseEntity.ok(mcRouterContainerService.getStatus());
        } catch (McRouterException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage()
                            + ". Use force=true to stop MC-Router anyway (servers will lose domain routing).");
        }
    }
}
