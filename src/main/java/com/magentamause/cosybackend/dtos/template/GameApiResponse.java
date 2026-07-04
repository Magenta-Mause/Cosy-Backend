package com.magentamause.cosybackend.dtos.template;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/** Envelope for the template-service {@code GET /v3/games} response: {@code {"games":[...]}}. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GameApiResponse(List<ExternalGameDto> games) {}
