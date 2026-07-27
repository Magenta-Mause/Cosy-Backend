package com.magentamause.cosybackend.configs.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param cookieSecure whether auth cookies carry the {@code Secure} attribute. Defaults to {@code
 *     false} because the installer's TLS mode is opt-in and a {@code Secure} cookie is silently
 *     dropped by browsers over plain HTTP, which would break login outright on the non-TLS installs
 *     that are still the default. Any deployment terminating TLS should set this to {@code true}.
 */
@ConfigurationProperties(prefix = "cosy.security")
public record SecurityProperties(boolean cookieSecure) {}
