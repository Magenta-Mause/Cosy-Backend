package com.magentamause.cosybackend.services;


import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.DummyInstantiatedEntity;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.utility.PortMapping;
import com.magentamause.cosybackend.repositories.DummyInstantiatedPropertiesRepository;
import com.magentamause.cosybackend.services.gameserver.GameServerService;
import com.magentamause.cosybackend.services.user.UserEntityService;
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
    private final GameServerService gameServerService;
    private final DummyInstantiatedPropertiesRepository dummyInstantiatedPropertiesRepository;
    private final UserEntityService userEntityService;
    private List<GameServerEntity> dummyGameServers;
    private UserEntity adminUser;

    @Autowired
    public DummyDataService(
            PasswordEncoder passwordEncoder,
            GameServerService gameServerService,
            UserEntityService userEntityService,
            DummyInstantiatedPropertiesRepository dummyInstantiatedPropertiesRepository) {
        this.passwordEncoder = passwordEncoder;
        this.gameServerService = gameServerService;
        this.dummyInstantiatedPropertiesRepository = dummyInstantiatedPropertiesRepository;
        this.userEntityService = userEntityService;

        this.adminUser =
                UserEntity.builder()
                        .username("admin")
                        .password(this.passwordEncoder.encode("admin"))
                        .defaultPasswordReset(false)
                        .role(UserEntity.Role.OWNER)
                        .build();

        this.dummyGameServers =
                List.of(
                        GameServerEntity.builder()
                                .uuid(UUID.randomUUID().toString())
                                .serverName("TOSIOS")
                                .owner(adminUser)
                                .status(GameServerDto.GameServerStatus.STOPPED)
                                .timestampLastStarted(LocalDateTime.now().minusHours(2))
                                .dockerImageName("halftheopposite/tosios")
                                .dockerImageTag("latest")
                                .portMappings(
                                        List.of(
                                                PortMapping.builder()
                                                        .instancePort(3001)
                                                        .containerPort(3001)
                                                        .protocol(PortMapping.PortProtocol.TCP)
                                                        .build()))
                                .environmentVariables(List.of())
                                .build());
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
        this.dummyGameServers.forEach(gameServerService::saveGameServer);

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
