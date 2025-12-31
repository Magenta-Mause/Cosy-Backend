package com.magentamause.cosybackend.services;

import static com.magentamause.cosybackend.entities.GameServerConfigurationEntity.GameServerStatus.*;

import com.magentamause.cosybackend.entities.DummyInstantiatedProperties;
import com.magentamause.cosybackend.entities.GameServerConfigurationEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.utility.EnvironmentVariableConfiguration;
import com.magentamause.cosybackend.entities.utility.PortMapping;
import com.magentamause.cosybackend.repositories.DummyInstantiatedPropertiesRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DummyDataService {

    private final PasswordEncoder passwordEncoder;
    private final GameServerConfigurationService gameServerConfigurationService;
    private final DummyInstantiatedPropertiesRepository dummyInstantiatedPropertiesRepository;
    private final UserEntityService userEntityService;
    private List<GameServerConfigurationEntity> dummyGameServers;
    private UserEntity adminUser;

    @Autowired
    public DummyDataService(
            PasswordEncoder passwordEncoder,
            GameServerConfigurationService gameServerConfigurationService,
            UserEntityService userEntityService,
            DummyInstantiatedPropertiesRepository dummyInstantiatedPropertiesRepository) {
        this.passwordEncoder = passwordEncoder;
        this.gameServerConfigurationService = gameServerConfigurationService;
        this.dummyInstantiatedPropertiesRepository = dummyInstantiatedPropertiesRepository;
        this.userEntityService = userEntityService;

        this.adminUser =
                UserEntity.builder()
                        .username("admin")
                        .password(this.passwordEncoder.encode("admin"))
                        .defaultPasswordReset(false)
                        .role(UserEntity.Role.OWNER)
                        .maxMemory(null)
                        .maxCpuCores(null)
                        .build();

        this.dummyGameServers =
                List.of(
                        GameServerConfigurationEntity.builder()
                                .uuid(UUID.randomUUID().toString())
                                .serverName("Minecraft Survival EU-1")
                                .owner(adminUser)
                                .status(RUNNING)
                                .timestampLastStarted(LocalDateTime.now().minusHours(2))
                                .gameUuid("game-minecraft")
                                .dockerImageName("itzg/minecraft-server")
                                .dockerImageTag("latest")
                                .dockerExecutionCommand(
                                        List.of("java", "-Xmx2G", "-jar", "server.jar", "nogui"))
                                .portMappings(
                                        List.of(
                                                PortMapping.builder()
                                                        .instancePort(25565)
                                                        .containerPort(25565)
                                                        .protocol(PortMapping.PortProtocol.TCP)
                                                        .build()))
                                .environmentVariables(
                                        List.of(
                                                EnvironmentVariableConfiguration.builder()
                                                        .key("EULA")
                                                        .value("TRUE")
                                                        .build(),
                                                EnvironmentVariableConfiguration.builder()
                                                        .key("MAX_PLAYERS")
                                                        .value("20")
                                                        .build()))
                                .build(),
                        GameServerConfigurationEntity.builder()
                                .uuid(UUID.randomUUID().toString())
                                .serverName("Valheim Dedicated #2")
                                .owner(adminUser)
                                .status(STOPPED)
                                .timestampLastStarted(LocalDateTime.now().minusDays(1))
                                .gameUuid("game-valheim")
                                .dockerImageName("lloesche/valheim-server")
                                .dockerImageTag("latest")
                                .dockerExecutionCommand(List.of("./start_server.sh"))
                                .portMappings(
                                        List.of(
                                                PortMapping.builder()
                                                        .instancePort(2456)
                                                        .containerPort(2456)
                                                        .protocol(PortMapping.PortProtocol.TCP)
                                                        .build(),
                                                PortMapping.builder()
                                                        .instancePort(2457)
                                                        .containerPort(2457)
                                                        .protocol(PortMapping.PortProtocol.TCP)
                                                        .build()))
                                .environmentVariables(
                                        List.of(
                                                EnvironmentVariableConfiguration.builder()
                                                        .key("SERVER_NAME")
                                                        .value("Valheim World 2")
                                                        .build(),
                                                EnvironmentVariableConfiguration.builder()
                                                        .key("WORLD_NAME")
                                                        .value("Midgard")
                                                        .build()))
                                .build(),
                        GameServerConfigurationEntity.builder()
                                .uuid(UUID.randomUUID().toString())
                                .serverName("CS2 Competitive Server")
                                .owner(adminUser)
                                .status(FAILED)
                                .timestampLastStarted(LocalDateTime.now().minusMinutes(30))
                                .gameUuid("game-cs2")
                                .dockerImageName("cm2network/cs2")
                                .dockerImageTag("stable")
                                .dockerExecutionCommand(
                                        List.of("./cs2.sh", "-console", "-game", "csgo"))
                                .portMappings(
                                        List.of(
                                                PortMapping.builder()
                                                        .instancePort(27015)
                                                        .containerPort(27015)
                                                        .protocol(PortMapping.PortProtocol.TCP)
                                                        .build()))
                                .environmentVariables(
                                        List.of(
                                                EnvironmentVariableConfiguration.builder()
                                                        .key("GSLT")
                                                        .value("XXXX-XXXX-XXXX")
                                                        .build(),
                                                EnvironmentVariableConfiguration.builder()
                                                        .key("TICKRATE")
                                                        .value("128")
                                                        .build()))
                                .build());
        ;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeDummyData() {
        log.info("Initializing dummy data...");

        initializeAdminUserEntity();
        populateGameServerDummies();

        log.info("Dummy data initialized.");
    }

    private void populateGameServerDummies() {
        if (dummyInstantiatedPropertiesRepository.findById("dummy-game-servers").isPresent()) {
            log.info("Dummy game servers already populated");
            return;
        }

        log.info("Populating dummy game servers");
        this.dummyGameServers.forEach(gameServerConfigurationService::saveGameServer);

        dummyInstantiatedPropertiesRepository.save(
                DummyInstantiatedProperties.builder().key("dummy-game-servers").build());
    }

    private void initializeAdminUserEntity() {
        if (dummyInstantiatedPropertiesRepository.findById("admin-user-entity").isPresent()) {
            log.info("Admin user entity already exists");
            return;
        }

        this.userEntityService.saveUserEntity(adminUser);

        dummyInstantiatedPropertiesRepository.save(
                DummyInstantiatedProperties.builder().key("admin-user-entity").build());

        log.info("Admin user entity initialized");
    }
}
