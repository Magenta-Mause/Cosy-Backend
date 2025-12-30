package com.magentamause.cosybackend.dtos.entitydtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
public class GameDto {
    @NotNull private int id;
    @NotBlank private String name;

    private String hero_url;
    private String logo_url;
}
