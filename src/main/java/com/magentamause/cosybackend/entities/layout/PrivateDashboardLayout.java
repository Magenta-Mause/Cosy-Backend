package com.magentamause.cosybackend.entities.layout;

import jakarta.persistence.*;
import java.util.List;
import lombok.*;
import lombok.experimental.SuperBuilder;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Getter
@Setter
@Entity
@SuperBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@AllArgsConstructor
@RequiredArgsConstructor
public class PrivateDashboardLayout extends Layout {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DashboardTypes layoutType;

    @Column private String metricType;

    private String title;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "private_dashboard_content",
            joinColumns = @JoinColumn(name = "private_dashboard_layout_id"))
    private List<KeyValueEntry> content;

    public boolean isValid() {
        if (layoutType == null) return false;

        return switch (layoutType) {
            case METRIC -> metricType != null;
            case FREETEXT -> content != null && !content.isEmpty();
            case LOGS -> true;
        };
    }
}
