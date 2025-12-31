package com.magentamause.cosybackend.dtos.actiondtos;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.annotations.ValidUsername;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserCreationDto {
    @ValidUsername private String username;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String password;

    private Long maxMemory;

    private Double maxCpuCores;
}
