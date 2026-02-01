package com.magentamause.cosybackend.repositories;

import com.magentamause.cosybackend.entities.GameServerEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameServerRepository extends JpaRepository<GameServerEntity, String> {
    List<GameServerEntity> findByLastStartedBy_Uuid(String userUuid);
}
