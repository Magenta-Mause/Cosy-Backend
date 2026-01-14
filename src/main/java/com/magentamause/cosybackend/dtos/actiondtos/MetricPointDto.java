package com.magentamause.cosybackend.dtos.actiondtos;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class  MetricPointDto{
    Instant time;
    Double value;
}