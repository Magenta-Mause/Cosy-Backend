package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.dtos.actiondtos.WebhookCreationDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerWebhookDto;
import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.RequireAccess;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.core.gameserver.WebhookService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/game-server/{uuid}/webhooks")
public class WebhookController {
    // TODO: refactor access checks with fine-grained permissions
    private final WebhookService webhookService;

    @GetMapping
    @RequireAccess(action = Action.READ, resource = Resource.GAME_SERVER)
    public ResponseEntity<List<GameServerWebhookDto>> getAllWebhooks(
            @PathVariable @ResourceId String uuid) {
        List<GameServerWebhookDto> webhooks = webhookService.getAllWebhooks(uuid);
        return ResponseEntity.ok(webhooks);
    }

    @PostMapping
    @RequireAccess(action = Action.CREATE, resource = Resource.GAME_SERVER)
    public ResponseEntity<GameServerWebhookDto> createWebhook(
            @PathVariable @ResourceId String uuid, @Valid @RequestBody WebhookCreationDto request) {
        GameServerWebhookDto created = webhookService.createWebhook(uuid, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{webhookId}")
    @RequireAccess(action = Action.DELETE, resource = Resource.GAME_SERVER)
    // TODO: refactor it with fine-granted permissions
    public ResponseEntity<Void> deleteWebhook(
            @PathVariable @ResourceId String uuid, @PathVariable String webhookId) {
        webhookService.deleteWebhook(uuid, webhookId);
        return ResponseEntity.noContent().build();
    }
}
