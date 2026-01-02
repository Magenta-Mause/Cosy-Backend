package com.magentamause.cosybackend.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cosy.games-api")
public record GamesApiConfig(String url) {}
