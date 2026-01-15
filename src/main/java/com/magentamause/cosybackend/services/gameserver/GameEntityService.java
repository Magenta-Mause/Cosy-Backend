package com.magentamause.cosybackend.services.gameserver;

import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.repositories.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GameEntityService {

    private final GameRepository gameRepository;

    public Optional<GameEntity> getGameFromUuid(String uuid) {
        return gameRepository.findById(uuid);
    }
}
