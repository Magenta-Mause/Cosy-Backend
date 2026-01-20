package com.magentamause.cosybackend.services.external.templates;

import com.magentamause.cosybackend.dtos.template.TemplateDto;
import com.magentamause.cosybackend.dtos.template.TemplateApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CosyTemplateApiService {

    private final WebClient cosyTemplateApiWebClient;

    public Mono<List<TemplateDto>> queryCosyTemplateApi() {
        return cosyTemplateApiWebClient.get()
                .uri(UriBuilder::build)
                .retrieve()
                .bodyToMono(TemplateApiResponse.class)
                .map(TemplateApiResponse::templates);
    }
}
