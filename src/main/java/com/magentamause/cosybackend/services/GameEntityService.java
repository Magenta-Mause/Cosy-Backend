package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.repositories.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GameEntityService {

    private final GameRepository gameRepository;

    public GameEntity getGameFromUuid(String uuid) {
        return gameRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Game not found for UUID: " + uuid));
    }
}
