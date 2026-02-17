package com.magentamause.cosybackend.configs.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cosy.defaults")
public record DefaultProperties(Admin admin) {

    public record Admin(
            String username,
            String password) {}
}
