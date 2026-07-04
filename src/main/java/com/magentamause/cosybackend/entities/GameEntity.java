package com.magentamause.cosybackend.entities;

import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
import com.magentamause.cosybackend.dtos.template.ExternalGameDto;
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

    // v3: games/<slug>.yaml filename; the identifier templates reference via game_id.
    private String slug;

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

    public static GameEntity fromExternalDto(ExternalGameDto externalGameDto) {
        return GameEntity.builder()
                .name(externalGameDto.name())
                .slug(externalGameDto.slug())
                .heroUrl(externalGameDto.heroUrl())
                .logoUrl(externalGameDto.logoUrl())
                .externalGameId(
                        externalGameDto.externalGameId() != null
                                ? externalGameDto.externalGameId()
                                : 0)
                .build();
    }
}
