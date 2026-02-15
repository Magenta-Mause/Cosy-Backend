package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.dtos.actiondtos.WebhookCreationDto;
import com.magentamause.cosybackend.dtos.entitydtos.WebhookDto;
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
@RequestMapping("/game-server/{gameserverUuid}/webhooks")
public class WebhookController {
    // TODO: refactor access checks with fine-grained permissions
    private final WebhookService webhookService;

    @GetMapping
    @RequireAccess(action = Action.READ, resource = Resource.GAME_SERVER)
    public ResponseEntity<List<WebhookDto>> getAllWebhooks(
            @PathVariable @ResourceId String gameserverUuid) {
        List<WebhookDto> webhooks = webhookService.getAllWebhooks(gameserverUuid);
        return ResponseEntity.ok(webhooks);
    }

    @PostMapping
    @RequireAccess(action = Action.CREATE, resource = Resource.GAME_SERVER)
    public ResponseEntity<WebhookDto> createWebhook(
            @PathVariable @ResourceId String gameserverUuid,
            @Valid @RequestBody WebhookCreationDto creationDto) {
        WebhookDto created = webhookService.createWebhook(gameserverUuid, creationDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{webhookUuid}")
    @RequireAccess(action = Action.DELETE, resource = Resource.GAME_SERVER)
    public ResponseEntity<Void> deleteWebhook(
            @PathVariable @ResourceId String gameserverUuid, @PathVariable String webhookUuid) {
        webhookService.deleteWebhook(webhookUuid);
        return ResponseEntity.noContent().build();
    }
}
