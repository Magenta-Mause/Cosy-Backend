package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.dtos.actiondtos.WebhookCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.WebhookUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.dtos.entitydtos.WebhookDto;
import com.magentamause.cosybackend.entities.GameServerEventType;
import com.magentamause.cosybackend.entities.WebhookEntity;
import com.magentamause.cosybackend.entities.WebhookType;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.services.core.gameserver.webhookSender.GameServerDomainEvent;
import com.magentamause.cosybackend.services.core.gameserver.webhookSender.WebhookSender;
import com.magentamause.cosybackend.websockets.GameServerUpdatePublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerWebhookService {

    private final GameServerRepository gameServerRepository;
    private final List<WebhookSender> webhookSenders;
    private final GameServerUpdatePublisher gameServerUpdatePublisher;

    public List<WebhookDto> getAllWebhooks(String gameServerUuid) {
        GameServerEntity gameServer =
                gameServerRepository
                        .findById(gameServerUuid)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Game server with uuid "
                                                        + gameServerUuid
                                                        + " not found"));
        return Optional.ofNullable(gameServer.getWebhooks()).orElse(new ArrayList<>()).stream()
                .map(WebhookEntity::toDto)
                .toList();
    }

    @Transactional
    public WebhookDto createWebhook(String gameServerUuid, WebhookCreationDto creationDto) {
        GameServerEntity gameServer =
                gameServerRepository
                        .findById(gameServerUuid)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Game server with uuid "
                                                        + gameServerUuid
                                                        + " not found"));
        WebhookEntity webhookEntity = creationDto.toEntity();

        if (gameServer.getWebhooks() == null) {
            gameServer.setWebhooks(new ArrayList<>());
        }
        gameServer.getWebhooks().add(webhookEntity);

        GameServerEntity savedGameServer = gameServerRepository.save(gameServer);
        WebhookDto savedWebhook =
                savedGameServer.getWebhooks().stream()
                        .filter(w -> w.getWebhookUrl().equals(webhookEntity.getWebhookUrl()))
                        .findFirst()
                        .orElseThrow()
                        .toDto();

        gameServerUpdatePublisher.publishGameServerUpdate(savedGameServer);
        return savedWebhook;
    }

    @Transactional
    public WebhookDto updateWebhook(
            String gameserverUuid, String webhookUuid, WebhookUpdateDto updateDto) {
        GameServerEntity gameServer =
                gameServerRepository
                        .findById(gameserverUuid)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Game server with uuid "
                                                        + gameserverUuid
                                                        + " not found"));

        WebhookEntity webhookEntity =
                Optional.ofNullable(gameServer.getWebhooks()).orElse(new ArrayList<>()).stream()
                        .filter(w -> w.getUuid().equals(webhookUuid))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                String.format(
                                                        "Webhook '%s' not found for game server '%s'",
                                                        webhookUuid, gameserverUuid)));

        updateDto.applyToEntity(webhookEntity);
        GameServerEntity savedGameServer = gameServerRepository.save(gameServer);
        WebhookDto updatedWebhook = webhookEntity.toDto();

        gameServerUpdatePublisher.publishGameServerUpdate(savedGameServer);
        return updatedWebhook;
    }

    @Transactional
    public void deleteWebhook(String gameServerUuid, String webhookId) {
        GameServerEntity gameServer =
                gameServerRepository
                        .findById(gameServerUuid)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Game server with uuid "
                                                        + gameServerUuid
                                                        + " not found"));

        boolean removed =
                Optional.ofNullable(gameServer.getWebhooks())
                        .orElse(new ArrayList<>())
                        .removeIf(w -> w.getUuid().equals(webhookId));

        if (!removed) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Webhook '"
                            + webhookId
                            + "' not found for game server '"
                            + gameServerUuid
                            + "'");
        }

        GameServerEntity savedGameServer = gameServerRepository.save(gameServer);
        gameServerUpdatePublisher.publishGameServerUpdate(savedGameServer);
    }

    public void dispatch(GameServerDomainEvent event) {
        GameServerEntity gameServer =
                gameServerRepository
                        .findById(event.serverId())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Game server with uuid "
                                                        + event.serverId()
                                                        + " not found"));

        List<WebhookEntity> webhooks =
                Optional.ofNullable(gameServer.getWebhooks()).orElse(new ArrayList<>());

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

    public void handleStatusTransition(
            String serverUuid,
            String serverName,
            GameServerDto.GameServerStatus previousStatus,
            GameServerDto.GameServerStatus newStatus) {
        mapStatusTransitionToEvent(previousStatus, newStatus)
                .ifPresent(
                        eventType ->
                                dispatch(
                                        new GameServerDomainEvent(
                                                serverUuid, serverName, eventType)));
    }

    private Optional<GameServerEventType> mapStatusTransitionToEvent(
            GameServerDto.GameServerStatus previousStatus, GameServerDto.GameServerStatus status) {
        if (previousStatus == status) {
            return Optional.empty();
        }

        return switch (status) {
            case RUNNING -> Optional.of(GameServerEventType.SERVER_STARTED);
            case STOPPED -> Optional.of(GameServerEventType.SERVER_STOPPED);
            case FAILED -> Optional.of(GameServerEventType.SERVER_FAILED);
            default -> Optional.empty();
        };
    }
}
