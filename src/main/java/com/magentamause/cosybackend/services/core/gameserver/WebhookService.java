package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.dtos.actiondtos.WebhookCreationDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerWebhookDto;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.GameServerWebhookEntity;
import com.magentamause.cosybackend.repositories.GameServerWebhookRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class WebhookService {

    private final GameServerWebhookRepository webhookRepository;
    private final GameServerService gameServerService;

    public List<GameServerWebhookDto> getAllWebhooks(String gameServerUuid) {
        gameServerService.getGameServerById(gameServerUuid);
        List<GameServerWebhookEntity> webhookEntities =
                webhookRepository.findByGameServer_Uuid(gameServerUuid);
        return webhookEntities.stream().map(GameServerWebhookEntity::toDto).toList();
    }

    public GameServerWebhookDto createWebhook(String gameServerUuid, WebhookCreationDto request) {
        GameServerEntity gameServer = gameServerService.getGameServerById(gameServerUuid);
        GameServerWebhookEntity webhookEntity = request.toEntity(gameServer);
        return webhookRepository.save(webhookEntity).toDto();
    }

    public void deleteWebhook(String gameServerUuid, String webhookId) {
        gameServerService.getGameServerById(gameServerUuid);
        long deleted = webhookRepository.deleteByUuidAndGameServer_Uuid(webhookId, gameServerUuid);
        if (deleted == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Webhook with uuid "
                            + webhookId
                            + " not found for game server "
                            + gameServerUuid);
        }
    }
}
