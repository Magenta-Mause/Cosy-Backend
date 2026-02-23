package com.magentamause.cosybackend.configs.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cosy.footer")
public record FooterProperties(
        String fullName, String email, String phone, String street, String city) {}
