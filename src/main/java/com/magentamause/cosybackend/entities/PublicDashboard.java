package com.magentamause.cosybackend.entities;

import com.magentamause.cosybackend.entities.layout.PublicDashboardLayout;
import jakarta.persistence.*;
import java.util.List;
import lombok.*;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Setter
@Getter
@Entity
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PublicDashboard {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    @Column(nullable = false)
    private boolean publicDashboardEnabled = false;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "public_dashboard_layout_uuid")
    @OrderColumn(name = "public_dashboard_layout_index")
    private List<PublicDashboardLayout> publicDashboardLayouts;
}
