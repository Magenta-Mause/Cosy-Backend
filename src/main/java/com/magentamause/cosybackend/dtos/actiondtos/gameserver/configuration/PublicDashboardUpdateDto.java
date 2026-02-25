package com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration;

import com.magentamause.cosybackend.entities.layout.PublicDashboardLayout;
import java.util.List;
import lombok.Data;

@Data
public class PublicDashboardUpdateDto {
    private boolean enabled;
    private List<PublicDashboardLayout> layouts;
}
