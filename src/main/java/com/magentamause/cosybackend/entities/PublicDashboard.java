package com.magentamause.cosybackend.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.magentamause.cosybackend.entities.layout.PublicDashboardLayout;
import jakarta.persistence.*;
import java.util.List;
import lombok.*;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Setter
@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PublicDashboard {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    @Column(nullable = false)
    @JsonProperty("public_dashboard_enabled")
    private boolean publicDashboardEnabled = false;

    @JsonProperty("public_dashboard_layouts")
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "public_dashboard_uuid")
    @OrderColumn(name = "public_dashboard_layout_index")
    private List<PublicDashboardLayout> publicDashboardLayouts;
}
