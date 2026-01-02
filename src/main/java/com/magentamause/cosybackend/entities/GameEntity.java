package com.magentamause.cosybackend.entities;

import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class GameEntity {

    @Id private int id;

    @Column(nullable = false)
    private String name;

    private String logoUrl;

    private String heroUrl;

    public static GameEntity fromDto(GameDto gameDto) {
        return GameEntity.builder()
                .id(gameDto.getId())
                .name(gameDto.getName())
                .heroUrl(gameDto.getHeroUrl())
                .logoUrl(gameDto.getLogoUrl())
                .build();
    }
}
