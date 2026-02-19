package com.magentamause.cosybackend.entities.layout;

import com.magentamause.cosybackend.entities.metric.MetricType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

@Getter
@Setter
@Entity
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PublicDashboardLayout extends Layout {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DashboardTypes publicDashboardTypes;

    @Enumerated(EnumType.STRING)
    private MetricType metricType;

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
