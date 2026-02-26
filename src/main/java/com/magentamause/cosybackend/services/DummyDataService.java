package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.configs.properties.DefaultProperties;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.GameServerCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.VolumeMountConfigurationDto;
import com.magentamause.cosybackend.entities.DummyInstantiatedEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.DockerHardwareLimits;
import com.magentamause.cosybackend.entities.gameserver.utility.GameServerDesign;
import com.magentamause.cosybackend.entities.gameserver.utility.PortMapping;
import com.magentamause.cosybackend.repositories.DummyInstantiatedPropertiesRepository;
import com.magentamause.cosybackend.services.core.gameserver.DefaultSettingsMapper;
import com.magentamause.cosybackend.services.core.gameserver.GameServerService;
import com.magentamause.cosybackend.services.user.UserEntityService;
import java.util.List;
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
    private final GameServerService gameServerService;
    private final DummyInstantiatedPropertiesRepository dummyInstantiatedPropertiesRepository;
    private final UserEntityService userEntityService;
    private final DefaultProperties defaultProperties;
    private List<GameServerCreationDto> dummyGameServers;
    private UserEntity adminUser;
    private DefaultSettingsMapper defaultSettingsMapper;

    @Autowired
    public DummyDataService(
            PasswordEncoder passwordEncoder,
            GameServerService gameServerService,
            UserEntityService userEntityService,
            DummyInstantiatedPropertiesRepository dummyInstantiatedPropertiesRepository,
            DefaultProperties defaultProperties,
            DefaultSettingsMapper defaultSettingsMapper) {
        this.passwordEncoder = passwordEncoder;
        this.gameServerService = gameServerService;
        this.dummyInstantiatedPropertiesRepository = dummyInstantiatedPropertiesRepository;
        this.userEntityService = userEntityService;
        this.defaultProperties = defaultProperties;

        this.adminUser =
                UserEntity.builder()
                        .username(defaultProperties.owner().username())
                        .password(this.passwordEncoder.encode(defaultProperties.owner().password()))
                        .defaultPasswordReset(false)
                        .role(UserEntity.Role.OWNER)
                        .build();

        this.dummyGameServers =
                List.of(
                        GameServerCreationDto.builder()
                                .serverName("TOSIOS - No Limits")
                                .design(GameServerDesign.CASTLE)
                                .dockerImageName("halftheopposite/tosios")
                                .dockerImageTag("latest")
                                .dockerHardwareLimits(DockerHardwareLimits.builder().build())
                                .portMappings(
                                        List.of(
                                                PortMapping.builder()
                                                        .instancePort(3001)
                                                        .containerPort(3001)
                                                        .protocol(PortMapping.PortProtocol.TCP)
                                                        .build()))
                                .environmentVariables(List.of())
                                .build(),
                        GameServerCreationDto.builder()
                                .serverName("TOSIOS - Memory Only")
                                .design(GameServerDesign.HOUSE)
                                .dockerImageName("halftheopposite/tosios")
                                .dockerImageTag("latest")
                                .dockerHardwareLimits(
                                        DockerHardwareLimits.builder()
                                                .dockerMemoryLimit("512MiB")
                                                .build())
                                .portMappings(
                                        List.of(
                                                PortMapping.builder()
                                                        .instancePort(3002)
                                                        .containerPort(3001)
                                                        .protocol(PortMapping.PortProtocol.TCP)
                                                        .build()))
                                .environmentVariables(List.of())
                                .build(),
                        GameServerCreationDto.builder()
                                .serverName("TOSIOS - CPU Only")
                                .design(GameServerDesign.CASTLE)
                                .dockerImageName("halftheopposite/tosios")
                                .dockerImageTag("latest")
                                .dockerHardwareLimits(
                                        DockerHardwareLimits.builder()
                                                .dockerMaxCpuCores(2f)
                                                .build())
                                .portMappings(
                                        List.of(
                                                PortMapping.builder()
                                                        .instancePort(3003)
                                                        .containerPort(3001)
                                                        .protocol(PortMapping.PortProtocol.TCP)
                                                        .build()))
                                .environmentVariables(List.of())
                                .build(),
                        GameServerCreationDto.builder()
                                .serverName("TOSIOS - Memory and CPU")
                                .design(GameServerDesign.HOUSE)
                                .dockerImageName("halftheopposite/tosios")
                                .dockerImageTag("latest")
                                .dockerHardwareLimits(
                                        DockerHardwareLimits.builder()
                                                .dockerMemoryLimit("512MiB")
                                                .dockerMaxCpuCores(2f)
                                                .build())
                                .portMappings(
                                        List.of(
                                                PortMapping.builder()
                                                        .instancePort(3004)
                                                        .containerPort(3001)
                                                        .protocol(PortMapping.PortProtocol.TCP)
                                                        .build()))
                                .environmentVariables(List.of())
                                .volumeMounts(
                                        List.of(
                                                VolumeMountConfigurationDto.builder()
                                                        .containerPath("/app/data")
                                                        .build()))
                                .build());
        this.defaultSettingsMapper = defaultSettingsMapper;
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
        this.dummyGameServers.forEach(
                (gameServer) -> {
                    gameServerService.createGameServer(adminUser, gameServer);
                });

        dummyInstantiatedPropertiesRepository.save(
                DummyInstantiatedEntity.builder().key("dummy-game-servers").build());
    }

    private void initializeAdminUserEntity() {
        if (dummyInstantiatedPropertiesRepository.findById("admin-user-entity").isPresent()) {
            log.info("Admin user entity already exists");
            return;
        }

        this.userEntityService.saveUserEntity(adminUser);

        dummyInstantiatedPropertiesRepository.save(
                DummyInstantiatedEntity.builder().key("admin-user-entity").build());

        log.info("Admin user entity initialized");
    }
}
