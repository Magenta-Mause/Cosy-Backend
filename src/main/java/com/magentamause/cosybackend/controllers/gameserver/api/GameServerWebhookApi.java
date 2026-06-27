package com.magentamause.cosybackend.controllers.gameserver.api;

import com.magentamause.cosybackend.dtos.actiondtos.WebhookCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.WebhookUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.WebhookDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Game Server Webhooks", description = "Webhook management for game servers")
@RequestMapping("/game-server/{gameserverUuid}/webhooks")
public interface GameServerWebhookApi {

    @Operation(summary = "Get all webhooks for a game server")
    @ApiResponse(responseCode = "200", description = "Webhooks returned")
    @GetMapping
    ResponseEntity<List<WebhookDto>> getAllWebhooks(
            @Parameter(description = "Game server UUID") @PathVariable String gameserverUuid);

    @Operation(summary = "Create a webhook for a game server")
    @ApiResponse(responseCode = "201", description = "Webhook created")
    @PostMapping
    ResponseEntity<WebhookDto> createWebhook(
            @Parameter(description = "Game server UUID") @PathVariable String gameserverUuid,
            @Valid @RequestBody WebhookCreationDto creationDto);

    @Operation(summary = "Update a webhook")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Webhook updated"),
        @ApiResponse(responseCode = "404", description = "Webhook not found")
    })
    @PutMapping("/{webhookUuid}")
    ResponseEntity<WebhookDto> updateWebhook(
            @Parameter(description = "Game server UUID") @PathVariable String gameserverUuid,
            @PathVariable String webhookUuid,
            @Valid @RequestBody WebhookUpdateDto updateDto);

    @Operation(summary = "Delete a webhook")
    @ApiResponse(responseCode = "204", description = "Webhook deleted")
    @DeleteMapping("/{webhookUuid}")
    ResponseEntity<Void> deleteWebhook(
            @Parameter(description = "Game server UUID") @PathVariable String gameserverUuid,
            @PathVariable String webhookUuid);
}
