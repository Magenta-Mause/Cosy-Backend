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
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

@Configuration
@EnableConfigurationProperties({
    LokiProperties.class,
    GamesApiProperties.class,
    CosyTemplateApiProperties.class
})
public class WebClientConfig {

    @Bean
    public WebClient lokiWebClient(LokiProperties lokiProperties) {
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(lokiProperties.url());
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);

        String encodedAuthorization =
                Base64.getEncoder()
                        .encodeToString(
                                (lokiProperties.username() + ":" + lokiProperties.password())
                                        .getBytes());

        return WebClient.builder()
                .uriBuilderFactory(factory)
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
    public WebClient webhookWebClient() {
        return WebClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
