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
public class PrivateDashboardLayout extends Layout {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DashboardTypes privateDashboardTypes;

    @Column private String metricType;

    private String title;

    @ElementCollection
    @CollectionTable(
            name = "private_dashboard_content",
            joinColumns = @JoinColumn(name = "private_dashboard_layout_id"))
    private List<KeyValue> content;

    public boolean isValid() {
        if (privateDashboardTypes == null) return false;

        return switch (privateDashboardTypes) {
            case METRIC -> metricType != null;
            case FREETEXT -> content != null && !content.isEmpty();
            case LOGS -> true;
        };
    }
}
