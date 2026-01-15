package com.magentamause.cosybackend.dtos.actiondtos;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MetricPointDto {
    Instant time;
    Double value;
}
