package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.dtos.actiondtos.WebhookCreationDto;
import com.magentamause.cosybackend.dtos.entitydtos.WebhookDto;
import com.magentamause.cosybackend.entities.WebhookEntity;
import com.magentamause.cosybackend.entities.WebhookType;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.repositories.WebhookRepository;
import com.magentamause.cosybackend.services.core.gameserver.webhookSender.GameServerDomainEvent;
import com.magentamause.cosybackend.services.core.gameserver.webhookSender.WebhookSender;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerWebhookService {

    private final WebhookRepository webhookRepository;
    private final GameServerService gameServerService;
    private final List<WebhookSender> webhookSenders;

    public List<WebhookDto> getAllWebhooks(String gameServerUuid) {
        gameServerService.getOrThrow(gameServerUuid);
        List<WebhookEntity> webhookEntities =
                webhookRepository.findByGameServer_Uuid(gameServerUuid);
        return webhookEntities.stream().map(WebhookEntity::toDto).toList();
    }

    public WebhookDto createWebhook(String gameServerUuid, WebhookCreationDto creationDto) {
        GameServerEntity gameServer = gameServerService.getOrThrow(gameServerUuid);
        WebhookEntity webhookEntity = creationDto.toEntity(gameServer);
        return webhookRepository.save(webhookEntity).toDto();
    }

    public void deleteWebhook(String webhookId) {
        webhookRepository.deleteById(webhookId);
    }

    public void dispatch(GameServerDomainEvent event) {
        List<WebhookEntity> webhooks = webhookRepository.findByGameServer_Uuid(event.serverId());
        for (WebhookEntity webhook : webhooks) {
            if (!webhook.isEnabled()
                    || !webhook.getSubscribedEvents().contains(event.eventType())) {
                continue;
            }

            Optional<WebhookSender> sender = resolveSender(webhook.getWebhookType());
            if (sender.isEmpty()) {
                log.warn(
                        "No webhook sender found for type {} on webhook {}",
                        webhook.getWebhookType(),
                        webhook.getUuid());
                continue;
            }

            try {
                sender.get().send(webhook, event);
            } catch (Exception ex) {
                log.error(
                        "Failed to dispatch webhook {} for event {}",
                        webhook.getUuid(),
                        event.eventType(),
                        ex);
            }
        }
    }

    private Optional<WebhookSender> resolveSender(WebhookType type) {
        return webhookSenders.stream().filter(sender -> sender.supports(type)).findFirst();
    }
}
