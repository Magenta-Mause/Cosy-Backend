package com.magentamause.cosybackend.configs;

import com.magentamause.cosybackend.configs.properties.CosyTemplateApiProperties;
import com.magentamause.cosybackend.configs.properties.GamesApiProperties;
import com.magentamause.cosybackend.configs.properties.LokiProperties;
import java.util.Base64;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

@Configuration
@EnableConfigurationProperties({
    LokiProperties.class,
    GamesApiProperties.class,
    CosyTemplateApiProperties.class
})
public class WebClientConfig {

    private static final int LOKI_MAX_IN_MEMORY_SIZE = 10 * 1024 * 1024;

    @Bean
    public WebClient lokiWebClient(LokiProperties lokiProperties) {
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(lokiProperties.url());
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);

        String encodedAuthorization =
                Base64.getEncoder()
                        .encodeToString(
                                (lokiProperties.username() + ":" + lokiProperties.password())
                                        .getBytes());

        ExchangeStrategies strategies =
                ExchangeStrategies.builder()
                        .codecs(
                                configurer ->
                                        configurer
                                                .defaultCodecs()
                                                .maxInMemorySize(LOKI_MAX_IN_MEMORY_SIZE))
                        .build();

        return WebClient.builder()
                .uriBuilderFactory(factory)
                .exchangeStrategies(strategies)
                .defaultHeaders(
                        headers -> {
                            headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuthorization);
                        })
                .build();
    }

    @Bean
    public WebClient gamesApiWebClient(GamesApiProperties gamesApiProperties) {
        return WebClient.builder()
                .baseUrl(gamesApiProperties.url())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public WebClient cosyTemplateApiWebClient(CosyTemplateApiProperties cosyTemplateApiProperties) {
        return WebClient.builder()
                .baseUrl(cosyTemplateApiProperties.url())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public WebClient cosyGamesApiWebClient(CosyTemplateApiProperties cosyTemplateApiProperties) {
        return WebClient.builder()
                .baseUrl(cosyTemplateApiProperties.gamesUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public WebClient webhookWebClient() {
        return WebClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
