package com.magentamause.cosybackend.entities;

import com.magentamause.cosybackend.dtos.entitydtos.CosyInstanceSettingsDto;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class CosyInstanceSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded @Builder.Default
    private McRouterConfiguration mcRouterConfiguration = new McRouterConfiguration();

    public CosyInstanceSettingsDto toDto() {
        return CosyInstanceSettingsDto.builder()
                .id(this.id)
                .mcRouterConfiguration(
                        this.mcRouterConfiguration != null
                                ? this.mcRouterConfiguration.toDto()
                                : null)
                .build();
    }
}
