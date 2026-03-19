package com.magentamause.cosybackend.dtos.actiondtos;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.entities.CosyInstanceSettingsEntity;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CosyInstanceSettingsUpdateDto {

    @Valid private McRouterConfigurationUpdateDto mcRouterConfiguration;

    public CosyInstanceSettingsEntity applyToEntity(CosyInstanceSettingsEntity entity) {
        if (this.mcRouterConfiguration != null) {
            entity.setMcRouterConfiguration(
                    this.mcRouterConfiguration.applyToEntity(entity.getMcRouterConfiguration()));
        }
        return entity;
    }
}
