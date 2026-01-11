package com.magentamause.cosybackend.configs;

import com.magentamause.cosybackend.configs.properties.LokiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;

@Configuration
@EnableConfigurationProperties({LokiProperties.class})
public class WebClientConfig {

    @Bean
    public WebClient lokiWebClient(LokiProperties lokiProperties) {
        String encodedAuthorization = Base64.getEncoder().encodeToString((lokiProperties.username() + ":" + lokiProperties.password()).getBytes());
        return WebClient.builder()
                .baseUrl(lokiProperties.url())
                .defaultHeaders(headers -> {
                    headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                    headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuthorization);
                }).build();
    }
}
