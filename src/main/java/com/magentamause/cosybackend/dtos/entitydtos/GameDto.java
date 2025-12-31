package com.magentamause.cosybackend.dtos.entitydtos;

import com.magentamause.cosybackend.entities.GameEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameDto {
    @NotNull private int id;
    @NotBlank private String name;

    private String hero_url;
    private String logo_url;

    public static GameDto fromEntity(GameEntity gameEntity) {
        return GameDto.builder()
                .id(gameEntity.getId())
                .name(gameEntity.getName())
                .hero_url(gameEntity.getHeroUrl())
                .logo_url(gameEntity.getLogoUrl())
                .build();
    }
}
