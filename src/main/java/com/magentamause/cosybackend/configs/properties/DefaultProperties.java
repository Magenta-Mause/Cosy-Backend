package com.magentamause.cosybackend.configs.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cosy.defaults")
public record DefaultProperties(Owner owner, boolean initializeDummyData) {

    public record Owner(String username, String password) {}
}
