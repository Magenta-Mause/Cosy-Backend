package com.magentamause.cosybackend.configs;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "cosy.cors")
public class CorsProperties {

    private List<String> allowedOrigins;
}
