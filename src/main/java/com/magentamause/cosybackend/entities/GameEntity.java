package com.magentamause.cosybackend.entities;

import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class GameEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    private int externalGameId;

    @Column(nullable = false)
    private String name;

    private String logoUrl;

    private String heroUrl;

    public static GameEntity fromDto(GameDto gameDto) {
        return GameEntity.builder()
                .name(gameDto.getName())
                .heroUrl(gameDto.getHeroUrl())
                .logoUrl(gameDto.getLogoUrl())
                .externalGameId(gameDto.getExternalGameId())
                .build();
    }
}
