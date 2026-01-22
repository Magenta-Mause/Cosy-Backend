package com.magentamause.cosybackend.services.external.templates;

import com.magentamause.cosybackend.dtos.template.ExternalTemplateDto;
import com.magentamause.cosybackend.dtos.template.TemplateApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class CosyTemplateApiService {

    private final WebClient cosyTemplateApiWebClient;

    public Mono<List<ExternalTemplateDto>> queryCosyTemplateApi() {
        return cosyTemplateApiWebClient
                .get()
                .uri(UriBuilder::build)
                .retrieve()
                .bodyToMono(TemplateApiResponse.class)
                .map(TemplateApiResponse::templates);
    }
}
