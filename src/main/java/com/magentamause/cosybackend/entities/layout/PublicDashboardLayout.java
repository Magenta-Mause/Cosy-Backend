package com.magentamause.cosybackend.entities.layout;

import jakarta.persistence.*;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Getter
@Setter
@Entity
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PublicDashboardLayout extends Layout {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DashboardTypes publicDashboardTypes;

    @Column private String metricType;

    private String title;

    @ElementCollection
    @CollectionTable(
            name = "public_dashboard_content",
            joinColumns = @JoinColumn(name = "public_dashboard_layout_id"))
    private List<KeyValue> content;

    public boolean isValid() {
        if (publicDashboardTypes == null) return false;

        return switch (publicDashboardTypes) {
            case METRIC -> metricType != null;
            case FREETEXT -> content != null && !content.isEmpty();
            case LOGS -> true;
        };
    }
}
