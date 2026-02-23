package com.magentamause.cosybackend.repositories;

import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameServerRepository extends JpaRepository<GameServerEntity, String> {
    List<GameServerEntity> findByOwner_Uuid(String userUuid);
}
