package com.magentamause.cosybackend.entities;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@Measurement(name = "metrics")
public class Metrics {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(tag = true)
    private String uuid;

    @Column(tag = true)
    private String name;

    @Column
    private Double cpuPercent;

    @Column
    private Long memoryUsage;

    @Column
    private Long memoryLimit;

    @Column
    private Double memoryPercent;

    @Column
    private Long networkInput;

    @Column
    private Long networkOutput;

    @Column
    private Long blockRead;

    @Column
    private Long blockWrite;

    @Column(timestamp = true)
    private Instant time;
}
