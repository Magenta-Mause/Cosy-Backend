package com.magentamause.cosybackend.dtos.actiondtos;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class MetricPointDto {
    private Instant time;
    private Double value;
}
