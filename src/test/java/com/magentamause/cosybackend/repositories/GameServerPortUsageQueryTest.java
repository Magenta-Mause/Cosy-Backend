package com.magentamause.cosybackend.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.PortMapping;
import com.magentamause.cosybackend.repositories.projections.GameServerPortUsage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The port usage query is a JPQL constructor expression over an element collection, which the
 * bootstrap only syntax-checks — this executes it, so a wrong projection or a join that silently
 * drops rows cannot pass unnoticed.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GameServerPortUsageQueryTest {

    @Autowired private GameServerRepository gameServerRepository;

    @Test
    void returnsOneRowPerConfiguredHostPort() {
        GameServerEntity server =
                gameServerRepository.save(
                        server(
                                "port-usage-server",
                                GameServerDto.GameServerStatus.RUNNING,
                                mapping(25565, PortMapping.PortProtocol.TCP),
                                mapping(25565, PortMapping.PortProtocol.UDP)));

        assertThat(usagesOf(server.getUuid()))
                .containsExactlyInAnyOrder(
                        new GameServerPortUsage(
                                server.getUuid(),
                                "port-usage-server",
                                GameServerDto.GameServerStatus.RUNNING,
                                25565,
                                PortMapping.PortProtocol.TCP),
                        new GameServerPortUsage(
                                server.getUuid(),
                                "port-usage-server",
                                GameServerDto.GameServerStatus.RUNNING,
                                25565,
                                PortMapping.PortProtocol.UDP));
    }

    @Test
    void skipsAServerThatPublishesNoPortAtAll() {
        GameServerEntity server =
                gameServerRepository.save(
                        server("portless-server", GameServerDto.GameServerStatus.STOPPED));

        assertThat(usagesOf(server.getUuid())).isEmpty();
    }

    private List<GameServerPortUsage> usagesOf(String uuid) {
        // The instance carries dummy servers of its own; only this server's rows are of interest.
        return gameServerRepository.findAllPortUsages().stream()
                .filter(usage -> usage.gameServerUuid().equals(uuid))
                .toList();
    }

    private GameServerEntity server(
            String name, GameServerDto.GameServerStatus status, PortMapping... mappings) {
        return GameServerEntity.builder()
                .serverName(name)
                .status(status)
                .dockerImageName("itzg/minecraft-server")
                .portMappings(List.of(mappings))
                .build();
    }

    private PortMapping mapping(int port, PortMapping.PortProtocol protocol) {
        return PortMapping.builder()
                .instancePort(port)
                .containerPort(port)
                .protocol(protocol)
                .build();
    }
}
