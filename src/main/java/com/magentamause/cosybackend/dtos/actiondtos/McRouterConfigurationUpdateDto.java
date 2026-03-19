package com.magentamause.cosybackend.dtos.actiondtos;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.entities.McRouterConfiguration;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class McRouterConfigurationUpdateDto {

    private Boolean enabled;

    @Min(value = 1, message = "Port must be at least 1")
    @Max(value = 65535, message = "Port must be at most 65535")
    private Integer port;

    private List<String> domains;

    public McRouterConfiguration applyToEntity(McRouterConfiguration entity) {
        if (entity == null) {
            entity = new McRouterConfiguration();
        }
        if (this.enabled != null) {
            entity.setEnabled(this.enabled);
        }
        if (this.port != null) {
            entity.setPort(this.port);
        }
        if (this.domains != null) {
            entity.setDomains(new ArrayList<>(this.domains));
        }
        return entity;
    }
}
