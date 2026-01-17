package com.magentamause.cosybackend.configs;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.magentamause.cosybackend.configs.properties.InfluxProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(InfluxProperties.class)
public class InfluxConfig {
    private final InfluxProperties influxProperties;

    @Bean(destroyMethod = "close")
    public InfluxDBClient influxDBClient() {
        return InfluxDBClientFactory.create(
                influxProperties.url(),
                influxProperties.token().toCharArray(),
                influxProperties.org(),
                influxProperties.bucket());
    }
}
