package com.magentamause.cosybackend.configs.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cosy.cors")
public record CorsProperties(List<String> allowedOrigins) {}
