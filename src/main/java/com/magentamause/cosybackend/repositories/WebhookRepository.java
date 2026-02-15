package com.magentamause.cosybackend.repositories;

import com.magentamause.cosybackend.entities.WebhookEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookRepository extends JpaRepository<WebhookEntity, String> {
    List<WebhookEntity> findByGameServer_Uuid(String gameServerUuid);

    long deleteByUuidAndGameServer_Uuid(String webhookId, String gameServerUuid);
}
