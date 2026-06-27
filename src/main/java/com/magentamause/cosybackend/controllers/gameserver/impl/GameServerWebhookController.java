package com.magentamause.cosybackend.controllers.gameserver.impl;

import com.magentamause.cosybackend.controllers.gameserver.api.GameServerWebhookApi;
import com.magentamause.cosybackend.dtos.actiondtos.WebhookCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.WebhookUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.WebhookDto;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.core.gameserver.GameServerWebhookService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class GameServerWebhookController implements GameServerWebhookApi {
    private final GameServerWebhookService webhookService;

    @Override
    @NeedsValidation(Operation.GAME_SERVER_WEBHOOK_READ)
    public ResponseEntity<List<WebhookDto>> getAllWebhooks(
            @ResourceId String gameserverUuid) {
        List<WebhookDto> webhooks = webhookService.getAllWebhooks(gameserverUuid);
        return ResponseEntity.ok(webhooks);
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_WEBHOOK_CREATE)
    public ResponseEntity<WebhookDto> createWebhook(
            @ResourceId String gameserverUuid,
            WebhookCreationDto creationDto) {
        WebhookDto created = webhookService.createWebhook(gameserverUuid, creationDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_WEBHOOK_UPDATE)
    public ResponseEntity<WebhookDto> updateWebhook(
            @ResourceId String gameserverUuid,
            String webhookUuid,
            WebhookUpdateDto updateDto) {
        WebhookDto updated = webhookService.updateWebhook(gameserverUuid, webhookUuid, updateDto);
        return ResponseEntity.ok(updated);
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_WEBHOOK_DELETE)
    public ResponseEntity<Void> deleteWebhook(
            @ResourceId String gameserverUuid, String webhookUuid) {
        webhookService.deleteWebhook(gameserverUuid, webhookUuid);
        return ResponseEntity.noContent().build();
    }
}
