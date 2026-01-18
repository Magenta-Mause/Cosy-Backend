package com.magentamause.cosybackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
public class CosyBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CosyBackendApplication.class, args);
    }
}
