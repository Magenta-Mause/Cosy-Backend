package com.magentamause.cosybackend.entities.metric;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@Measurement(name = "metrics")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Metric {
    @Column(tag = true)
    private String uuid;

    @Column(tag = true)
    private String name;

    @Column private Double cpuPercent;

    @Column private Long memoryUsage;

    @Column private Long memoryLimit;

    @Column private Double memoryPercent;

    @Column private Long networkInput;

    @Column private Long networkOutput;

    @Column private Long blockRead;

    @Column private Long blockWrite;

    @Column(timestamp = true)
    private Instant time;
}

