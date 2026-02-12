package com.magentamause.cosybackend.entities.layout.privatedashboard;

import com.magentamause.cosybackend.entities.layout.Layout;
import com.magentamause.cosybackend.entities.metric.MetricType;
import jakarta.persistence.*;
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
    private PrivateDashboardTypes privateDashboardTypes;

    @Enumerated(EnumType.STRING)
    private MetricType metricType;

    private String title;

    private String content;

    public boolean isValid() {
        if (privateDashboardTypes == null) return false;

        return switch (privateDashboardTypes) {
            case METRIC -> metricType != null;
            case FREETEXT -> content != null && !content.isBlank() && title != null && !title.isBlank();
            case LOGS -> true;
        };
    }
}
