package com.magentamause.cosybackend.configs;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class InfluxConfig {
    private InfluxDBClient influxDBClient;

    @Value("${influx.url}")
    private String url;

    @Value("${influx.token}")
    private String token;

    @Value("${influx.org}")
    private String org;

    @Value("${influx.bucket}")
    private String bucket;

    @PostConstruct
    public void init() {
        this.influxDBClient = InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
    }

    public InfluxDBClient getClient() {
        return influxDBClient;
    }

    @PreDestroy
    public void close() {
        if (influxDBClient != null) {
            influxDBClient.close();
        }
    }
}
