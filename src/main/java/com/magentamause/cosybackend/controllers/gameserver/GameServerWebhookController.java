package com.magentamause.cosybackend.controllers.gameserver;

import com.magentamause.cosybackend.dtos.actiondtos.WebhookCreationDto;
import com.magentamause.cosybackend.dtos.entitydtos.WebhookDto;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.core.gameserver.GameServerWebhookService;
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
public class GameServerWebhookController {
    private final GameServerWebhookService webhookService;

    @GetMapping
    @NeedsValidation(Operation.GAME_SERVER_WEBHOOK_READ)
    public ResponseEntity<List<WebhookDto>> getAllWebhooks(
            @PathVariable @ResourceId String gameserverUuid) {
        List<WebhookDto> webhooks = webhookService.getAllWebhooks(gameserverUuid);
        return ResponseEntity.ok(webhooks);
    }

    @PostMapping
    @NeedsValidation(Operation.GAME_SERVER_WEBHOOK_UPDATE)
    public ResponseEntity<WebhookDto> createWebhook(
            @PathVariable @ResourceId String gameserverUuid,
            @Valid @RequestBody WebhookCreationDto creationDto) {
        WebhookDto created = webhookService.createWebhook(gameserverUuid, creationDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{webhookUuid}")
    @NeedsValidation(Operation.GAME_SERVER_WEBHOOK_UPDATE)
    public ResponseEntity<Void> deleteWebhook(
            @PathVariable @ResourceId String gameserverUuid, @PathVariable String webhookUuid) {
        webhookService.deleteWebhook(webhookUuid);
        return ResponseEntity.noContent().build();
    }
}
