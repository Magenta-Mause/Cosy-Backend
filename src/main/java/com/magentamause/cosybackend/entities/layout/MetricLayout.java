package com.magentamause.cosybackend.entities.layout;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

@Getter
@Setter
@Entity
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MetricLayout extends Layout {

    @Column(nullable = false)
    private String metricType;
}
