package com.magentamause.cosybackend.services.external.templates;

import com.magentamause.cosybackend.dtos.template.ExternalGameDto;
import com.magentamause.cosybackend.dtos.template.ExternalTemplateDto;
import com.magentamause.cosybackend.dtos.template.GameApiResponse;
import com.magentamause.cosybackend.dtos.template.TemplateApiResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class CosyTemplateApiService {

    private final WebClient cosyTemplateApiWebClient;
    private final WebClient cosyGamesApiWebClient;

    public CosyTemplateApiService(
            @Qualifier("cosyTemplateApiWebClient") WebClient cosyTemplateApiWebClient,
            @Qualifier("cosyGamesApiWebClient") WebClient cosyGamesApiWebClient) {
        this.cosyTemplateApiWebClient = cosyTemplateApiWebClient;
        this.cosyGamesApiWebClient = cosyGamesApiWebClient;
    }

    /** Fetches the raw v3 templates ({@code {"templates":[...]}}); {@code {{...}}} preserved. */
    public Mono<List<ExternalTemplateDto>> queryCosyTemplateApi() {
        return cosyTemplateApiWebClient
                .get()
                .uri(UriBuilder::build)
                .retrieve()
                .bodyToMono(TemplateApiResponse.class)
                .map(TemplateApiResponse::templates);
    }

    /** Fetches the games index from the template-service ({@code {"games":[...]}}). */
    public Mono<List<ExternalGameDto>> queryCosyGamesApi() {
        return cosyGamesApiWebClient
                .get()
                .uri(UriBuilder::build)
                .retrieve()
                .bodyToMono(GameApiResponse.class)
                .map(GameApiResponse::games);
    }
}
