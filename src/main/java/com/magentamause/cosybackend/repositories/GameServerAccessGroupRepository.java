package com.magentamause.cosybackend.repositories;

import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameServerAccessGroupRepository
                extends JpaRepository<GameServerAccessGroupEntity, String> {
}
