package com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration;

import com.magentamause.cosybackend.entities.layout.PublicDashboardLayout;
import lombok.Data;

import java.util.List;

@Data
public class PublicDashboardUpdateDto {
    private boolean enabled;
    private List<PublicDashboardLayout> layouts;
}
