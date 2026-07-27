package com.magentamause.cosybackend.services.core.gameserver;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.magentamause.cosybackend.configs.properties.EngineProperties;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.PortMapping;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.exceptions.PortInUseException;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.services.core.games.GamesService;
import com.magentamause.cosybackend.services.core.logs.GameServerLogService;
import com.magentamause.cosybackend.services.engine.EngineManager;
import com.magentamause.cosybackend.services.engine.docker.util.HardwareLimitPresentValidator;
import com.magentamause.cosybackend.services.engine.docker.util.HardwareQuotaChecker;
import com.magentamause.cosybackend.services.engine.docker.util.VolumeDirectoryService;
import com.magentamause.cosybackend.services.technical.RCONService;
import com.magentamause.cosybackend.services.user.UserEntityService;
import com.magentamause.cosybackend.websockets.GameServerDockerProgressPublisher;
import com.magentamause.cosybackend.websockets.GameServerUpdatePublisher;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * A start that would collide with an occupied host port has to fail loudly and early: the engine
 * must never be asked to create the container, the server log must say what happened, and the
 * server must stay startable once the port frees up.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameServerServiceStartPortTest {

    private static final String SERVER_UUID = "server-uuid";
    private static final String OWNER_UUID = "owner-uuid";

    @Mock private GameServerRepository gameServerRepository;
    @Mock private UserEntityService userEntityService;
    @Mock private EngineManager engineManager;
    @Mock private GameServerUpdatePublisher gameServerUpdatePublisher;
    @Mock private GameServerDockerProgressPublisher dockerProgressPublisher;
    @Mock private GameServerLogService gameServerLogService;
    @Mock private GamesService gamesService;
    @Mock private HardwareLimitPresentValidator hardwareLimitValidator;
    @Mock private HardwareQuotaChecker hardwareQuotaChecker;
    @Mock private GameServerPortChecker portChecker;
    @Mock private VolumeDirectoryService volumeDirectoryService;
    @Mock private RCONService rconService;
    @Mock private DefaultSettingsMapper defaultSettingsMapper;
    @Mock private GameServerWebhookService webhookService;

    private GameServerService service;
    private GameServerEntity server;

    @BeforeEach
    void setUp() {
        service =
                new GameServerService(
                        gameServerRepository,
                        userEntityService,
                        engineManager,
                        gameServerUpdatePublisher,
                        dockerProgressPublisher,
                        gameServerLogService,
                        gamesService,
                        hardwareLimitValidator,
                        hardwareQuotaChecker,
                        portChecker,
                        volumeDirectoryService,
                        rconService,
                        defaultSettingsMapper,
                        webhookService,
                        new EngineProperties(
                                null, new EngineProperties.Reconciliation(180000, 60000)));

        server =
                GameServerEntity.builder()
                        .uuid(SERVER_UUID)
                        .serverName("server")
                        .status(GameServerDto.GameServerStatus.STOPPED)
                        .owner(UserEntity.builder().uuid(OWNER_UUID).build())
                        .portMappings(
                                List.of(
                                        PortMapping.builder()
                                                .instancePort(25565)
                                                .containerPort(25565)
                                                .protocol(PortMapping.PortProtocol.TCP)
                                                .build()))
                        .build();
        when(gameServerRepository.findById(SERVER_UUID)).thenReturn(Optional.of(server));
        when(gameServerRepository.save(any(GameServerEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void neverAsksTheEngineToStartAServerWhoseHostPortIsTaken() {
        givenPortConflict();

        assertThatThrownBy(() -> service.startServer(SERVER_UUID))
                .isInstanceOf(PortInUseException.class);

        verifyNoInteractions(engineManager);
        verify(gameServerRepository, never()).save(any(GameServerEntity.class));
    }

    @Test
    void recordsTheReasonInTheServerLog() {
        givenPortConflict();

        assertThatThrownBy(() -> service.startServer(SERVER_UUID));

        verify(gameServerLogService)
                .publishAndSaveLog(
                        eq(server),
                        eq(GameServerLogMessageEntity.LogLevel.COSY_DEBUG),
                        org.mockito.ArgumentMatchers.contains("25565/TCP"),
                        eq(false));
    }

    @Test
    void leavesTheServerStartableAfterARejectedStart() {
        givenPortConflict();

        assertThatThrownBy(() -> service.startServer(SERVER_UUID));

        // A second attempt must fail over the port again, not over a start that is still
        // considered in flight.
        assertThatThrownBy(() -> service.startServer(SERVER_UUID))
                .isInstanceOf(PortInUseException.class);
    }

    private void givenPortConflict() {
        doThrow(
                        new PortInUseException(
                                List.of(
                                        new GameServerPortChecker.PortConflict(
                                                25565,
                                                PortMapping.PortProtocol.TCP,
                                                "container foreign"))))
                .when(portChecker)
                .assertPortsAvailable(any(GameServerEntity.class));
    }
}
