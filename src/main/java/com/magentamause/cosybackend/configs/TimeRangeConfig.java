package com.magentamause.cosybackend.configs;

import com.magentamause.cosybackend.configs.properties.TimeRangeProperties;
import com.magentamause.cosybackend.services.core.timerange.TimeRangeResolver;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TimeRangeProperties.class)
public class TimeRangeConfig {

    @Bean
    public TimeRangeResolver timeRangeResolver(TimeRangeProperties properties) {
        return new TimeRangeResolver(properties, Clock.systemUTC());
    }
}
