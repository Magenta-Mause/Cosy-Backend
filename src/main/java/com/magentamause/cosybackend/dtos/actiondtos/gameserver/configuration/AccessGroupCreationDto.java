package com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class AccessGroupCreationDto {
    @NotEmpty
    private String name;
}
