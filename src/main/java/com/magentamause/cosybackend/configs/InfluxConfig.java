package com.magentamause.cosybackend.configs;

import com.influxdb.client.InfluxDBClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class InfluxConfig {
    private InfluxDBClient influxDBClient;

    @Value("${influx.url}")
    private String url;

    @Value("${influx.token}")
    private String token;

    @Value("${influx.org}")
    private String org;

    public InfluxDBClient getClient() {
        return influxDBClient;
    }

    public void close() {
        if  (influxDBClient != null) {
            influxDBClient.close();
        }
    }
}
