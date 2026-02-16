package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.RCONConfiguration;
import com.magentamause.cosybackend.entities.layout.MetricLayout;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GameServerConfigurationService {

        private final GameServerRepository gameServerRepository;
        private final GameServerService gameServerService;

        public GameServerEntity updateRconConfig(String uuid, RCONConfiguration updateDto) {
                GameServerEntity gameServer = gameServerService.getOrThrow(uuid);
                gameServer.setRconConfiguration(updateDto);
                return gameServerService.saveGameServerConfiguration(gameServer, false);
        }

        public void updateMetricLayout(String gameServerUuid, List<MetricLayout> metricLayout) {
                GameServerEntity gameServer = gameServerRepository
                                .findById(gameServerUuid)
                                .orElseThrow(
                                                () -> new ResponseStatusException(
                                                                HttpStatus.NOT_FOUND,
                                                                "Server '" + gameServerUuid + "' not found"));

                gameServer.getMetricLayout().clear();
                gameServer.getMetricLayout().addAll(metricLayout);
                gameServerRepository.save(gameServer);
        }
}
