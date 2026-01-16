package com.magentamause.cosybackend.services.gameserver;

import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.repositories.GameRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GameEntityService {

    private final GameRepository gameRepository;

    public Optional<GameEntity> getGameFromUuid(String uuid) {
        return gameRepository.findById(uuid);
    }
}
