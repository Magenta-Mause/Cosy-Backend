package com.magentamause.cosybackend.dtos.template;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TemplateApiResponse(List<ExternalTemplateDto> templates) {}
