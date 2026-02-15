package com.magentamause.cosybackend.repositories;

import com.magentamause.cosybackend.entities.GameServerWebhookEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameServerWebhookRepository
        extends JpaRepository<GameServerWebhookEntity, String> {
    List<GameServerWebhookEntity> findByGameServer_Uuid(String gameServerUuid);

    long deleteByUuidAndGameServer_Uuid(String webhookId, String gameServerUuid);
}
