package com.magentamause.cosybackend.dtos.template;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A game entry from the template-service {@code GET /v3/games} response. {@code external_game_id}
 * is the optional SteamGridDB numeric id; {@code slug} is the {@code games/<slug>.yaml} filename
 * and is always present.
 */
public record ExternalGameDto(
        String name,
        @JsonProperty("logo_url") String logoUrl,
        @JsonProperty("hero_url") String heroUrl,
        @JsonProperty("external_game_id") Integer externalGameId,
        String slug) {}
