package com.magentamause.cosybackend.entities;

import com.magentamause.cosybackend.entities.layout.PublicDashboardLayout;
import jakarta.persistence.*;
import java.util.List;
import lombok.*;

@Setter
@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicDashboard {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "public_dashboard_uuid")
    @OrderColumn(name = "public_dashboard_layout_index")
    private List<PublicDashboardLayout> layouts;
}
