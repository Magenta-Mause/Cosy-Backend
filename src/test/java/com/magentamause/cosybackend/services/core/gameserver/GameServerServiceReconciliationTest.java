package com.magentamause.cosybackend.services.core.gameserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.magentamause.cosybackend.configs.properties.EngineProperties;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
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
 * Reconciliation is what actually repairs the damage of a lost engine event: the status update that
 * was never delivered is re-derived from the real container state, instead of leaving the server
 * stuck in a transitional status until the backend is restarted.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameServerServiceReconciliationTest {

    private static final String SERVER_UUID = "server-uuid";
    private static final long RECONCILIATION_INTERVAL_MS = 180000;
    private static final long GRACE_PERIOD_MS = 60000;

    @Mock private GameServerRepository gameServerRepository;
    @Mock private UserEntityService userEntityService;
    @Mock private EngineManager engineManager;
    @Mock private GameServerUpdatePublisher gameServerUpdatePublisher;
    @Mock private GameServerDockerProgressPublisher dockerProgressPublisher;
    @Mock private GameServerLogService gameServerLogService;
    @Mock private GamesService gamesService;
    @Mock private HardwareLimitPresentValidator hardwareLimitValidator;
    @Mock private HardwareQuotaChecker hardwareQuotaChecker;
    @Mock private VolumeDirectoryService volumeDirectoryService;
    @Mock private RCONService rconService;
    @Mock private DefaultSettingsMapper defaultSettingsMapper;
    @Mock private GameServerWebhookService webhookService;

    private GameServerService service;

    @BeforeEach
    void setUp() {
        EngineProperties engineProperties =
                new EngineProperties(
                        null,
                        new EngineProperties.Reconciliation(
                                RECONCILIATION_INTERVAL_MS, GRACE_PERIOD_MS));
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
                        volumeDirectoryService,
                        rconService,
                        defaultSettingsMapper,
                        webhookService,
                        engineProperties);
        when(gameServerRepository.save(any(GameServerEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void repairsAServerStuckInATransitionalStatusWhoseContainerIsRunning() {
        GameServerEntity server = givenServer(GameServerDto.GameServerStatus.AWAITING_UPDATE);
        givenEngineStatus(GameServerDto.GameServerStatus.RUNNING);

        service.reconcileAllStatuses();

        assertThat(server.getStatus()).isEqualTo(GameServerDto.GameServerStatus.RUNNING);
        verify(gameServerRepository).save(server);
    }

    @Test
    void repairsAServerStuckInStoppingWhoseContainerIsGone() {
        GameServerEntity server = givenServer(GameServerDto.GameServerStatus.STOPPING);
        givenEngineStatus(GameServerDto.GameServerStatus.STOPPED);

        service.reconcileAllStatuses();

        assertThat(server.getStatus()).isEqualTo(GameServerDto.GameServerStatus.STOPPED);
    }

    @Test
    void leavesAServerWhoseStatusAlreadyMatchesUntouched() {
        GameServerEntity server = givenServer(GameServerDto.GameServerStatus.RUNNING);
        givenEngineStatus(GameServerDto.GameServerStatus.RUNNING);

        service.reconcileAllStatuses();

        verify(gameServerRepository, never()).save(any(GameServerEntity.class));
    }

    @Test
    void doesNotDowngradeAFailedServerToStopped() {
        GameServerEntity server = givenServer(GameServerDto.GameServerStatus.FAILED);
        givenEngineStatus(GameServerDto.GameServerStatus.STOPPED);

        service.reconcileAllStatuses();

        assertThat(server.getStatus()).isEqualTo(GameServerDto.GameServerStatus.FAILED);
        verify(gameServerRepository, never()).save(any(GameServerEntity.class));
    }

    @Test
    void doesNotFightAnInFlightStartWithinTheGracePeriod() {
        GameServerEntity server = givenServer(GameServerDto.GameServerStatus.STOPPED);
        givenEngineStatus(GameServerDto.GameServerStatus.STOPPED);
        // The start operation has just published AWAITING_UPDATE; the container does not exist yet.
        service.updateStatus(server, GameServerDto.GameServerStatus.AWAITING_UPDATE);

        service.reconcileAllStatuses();

        assertThat(server.getStatus()).isEqualTo(GameServerDto.GameServerStatus.AWAITING_UPDATE);
        verify(gameServerRepository).save(server);
    }

    @Test
    void attachesTheLogStreamOfARunningServerThatHasNone() {
        GameServerEntity server = givenServer(GameServerDto.GameServerStatus.RUNNING);
        givenEngineStatus(GameServerDto.GameServerStatus.RUNNING);
        when(engineManager.isLogListenerAttached(SERVER_UUID)).thenReturn(false);

        service.reconcileAllStatuses();

        verify(engineManager).attachLogListener(any(GameServerEntity.class), any());
    }

    @Test
    void doesNotAttachASecondLogStreamToARunningServer() {
        GameServerEntity server = givenServer(GameServerDto.GameServerStatus.RUNNING);
        givenEngineStatus(GameServerDto.GameServerStatus.RUNNING);
        when(engineManager.isLogListenerAttached(SERVER_UUID)).thenReturn(true);

        service.reconcileAllStatuses();

        verify(engineManager, never()).attachLogListener(any(GameServerEntity.class), any());
    }

    @Test
    void registersReconciliationAsTheReconnectListener() {
        when(gameServerRepository.findAll()).thenReturn(List.of());

        service.init();

        verify(engineManager).attachConnectionListener(any(Runnable.class));
    }

    private GameServerEntity givenServer(GameServerDto.GameServerStatus status) {
        GameServerEntity server =
                GameServerEntity.builder().uuid(SERVER_UUID).status(status).build();
        when(gameServerRepository.findAll()).thenReturn(List.of(server));
        when(gameServerRepository.findById(SERVER_UUID)).thenReturn(Optional.of(server));
        return server;
    }

    private void givenEngineStatus(GameServerDto.GameServerStatus status) {
        when(engineManager.getStatus(any(GameServerEntity.class))).thenReturn(status);
        when(engineManager.isLogListenerAttached(anyString())).thenReturn(true);
    }
}
