package com.magentamause.cosybackend.repositories;

import com.magentamause.cosybackend.entities.GameServerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameServerRepository extends JpaRepository<GameServerEntity, String> {}
