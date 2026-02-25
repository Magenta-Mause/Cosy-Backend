package com.magentamause.cosybackend.configs.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cosy.defaults")
public record DefaultProperties(Owner owner) {

    public record Owner(String username, String password) {}
}
