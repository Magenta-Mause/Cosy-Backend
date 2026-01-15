package com.magentamause.cosybackend.configs.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cosy.influx")
public record InfluxProperties(String url, String token, String org, String bucket) {}
