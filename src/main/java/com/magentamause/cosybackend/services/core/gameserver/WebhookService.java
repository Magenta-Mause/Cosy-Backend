package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.dtos.actiondtos.WebhookCreationDto;
import com.magentamause.cosybackend.dtos.entitydtos.WebhookDto;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.WebhookEntity;
import com.magentamause.cosybackend.repositories.WebhookRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookRepository webhookRepository;
    private final GameServerService gameServerService;

    public List<WebhookDto> getAllWebhooks(String gameServerUuid) {
        gameServerService.getGameServerById(gameServerUuid);
        List<WebhookEntity> webhookEntities =
                webhookRepository.findByGameServer_Uuid(gameServerUuid);
        return webhookEntities.stream().map(WebhookEntity::toDto).toList();
    }

    public WebhookDto createWebhook(String gameServerUuid, WebhookCreationDto creationDto) {
        GameServerEntity gameServer = gameServerService.getGameServerById(gameServerUuid);
        WebhookEntity webhookEntity = creationDto.toEntity(gameServer);
        return webhookRepository.save(webhookEntity).toDto();
    }

    public void deleteWebhook(String webhookId) {
        webhookRepository.deleteById(webhookId);
    }
}
