package com.magentamause.cosybackend.services.engine.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.EventsCmd;
import com.github.dockerjava.api.model.Event;
import com.github.dockerjava.api.model.EventActor;
import com.magentamause.cosybackend.configs.properties.EngineProperties;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.services.core.gameserver.GameServerStatusUpdateEventType;
import com.magentamause.cosybackend.services.engine.docker.util.DockerContainerNameResolver;
import com.magentamause.cosybackend.services.engine.docker.util.ReconnectBackoff;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the resilience contract of the Docker event subscription: it must never die silently, a
 * (re)connect must trigger reconciliation, and shutting down must stop the reconnect loop.
 *
 * <p>The docker-java client is mocked, so no Docker daemon is involved.
 */
class DockerEventHandlerTest {

    private static final String CONTAINER_PREFIX = "cosy-";
    private static final String SERVER_UUID = "server-uuid";
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration NO_ACTION_WINDOW = Duration.ofMillis(300);

    private final List<ResultCallback<Event>> callbacks = new CopyOnWriteArrayList<>();
    private final List<String> subscribedActions = new CopyOnWriteArrayList<>();

    private DockerEventHandler handler;

    @BeforeEach
    void setUp() {
        DockerClient client = mock(DockerClient.class);
        EventsCmd eventsCmd = mock(EventsCmd.class, RETURNS_SELF);
        when(client.eventsCmd()).thenReturn(eventsCmd);
        when(eventsCmd.withEventFilter(any(String[].class)))
                .thenAnswer(
                        invocation -> {
                            subscribedActions.clear();
                            for (Object action : invocation.getArguments()) {
                                subscribedActions.add((String) action);
                            }
                            return eventsCmd;
                        });
        when(eventsCmd.exec(any()))
                .thenAnswer(
                        invocation -> {
                            ResultCallback<Event> callback = invocation.getArgument(0);
                            callbacks.add(callback);
                            return callback;
                        });

        handler = new DockerEventHandler(client, containerNameResolver(), immediateBackoff());
        handler.init();
    }

    @AfterEach
    void tearDown() throws IOException {
        handler.close();
    }

    @Test
    void subscribesOnStartup() {
        assertThat(callbacks).hasSize(1);
    }

    @Test
    void doesNotSubscribeToTheStopActionBecauseDieAlreadyCoversIt() {
        assertThat(subscribedActions).contains("die").doesNotContain("stop");
    }

    @Test
    void reconnectsAfterTheStreamFails() {
        currentCallback().onError(new IOException("read timed out"));

        awaitSubscriptions(2);
    }

    @Test
    void reconnectsAfterTheStreamCompletes() {
        currentCallback().onComplete();

        awaitSubscriptions(2);
    }

    @Test
    void reconnectTriggersReconciliation() throws InterruptedException {
        CountDownLatch reconciled = new CountDownLatch(1);
        handler.attachConnectionListener(reconciled::countDown);

        currentCallback().onError(new IOException("read timed out"));

        assertThat(reconciled.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                .as("reconciliation must run after the subscription was re-established")
                .isTrue();
    }

    @Test
    void aTerminatedStreamReconnectsOnlyOnce() {
        ResultCallback<Event> callback = currentCallback();
        callback.onError(new IOException("read timed out"));
        callback.onComplete();

        awaitSubscriptions(2);
        sleep(NO_ACTION_WINDOW);
        assertThat(callbacks).hasSize(2);
    }

    @Test
    void shutdownStopsTheReconnectLoop() throws IOException {
        ResultCallback<Event> callback = currentCallback();
        handler.close();

        callback.onError(new IOException("stream closed by shutdown"));

        sleep(NO_ACTION_WINDOW);
        assertThat(callbacks).hasSize(1);
    }

    @Test
    void aStartEventReportsTheServerAsStarted() {
        List<GameServerStatusUpdateEventType> received = recordStatusUpdates();

        currentCallback().onNext(containerEvent("start", null));

        assertThat(received).containsExactly(GameServerStatusUpdateEventType.STARTED);
    }

    @Test
    void aDieEventDuringAnIntentionalStopReportsTheServerAsStopped() {
        List<GameServerStatusUpdateEventType> received = recordStatusUpdates();
        handler.attachStatusSupplier(SERVER_UUID, () -> GameServerDto.GameServerStatus.STOPPING);

        currentCallback().onNext(containerEvent("die", "143"));

        assertThat(received).containsExactly(GameServerStatusUpdateEventType.STOPPED);
    }

    @Test
    void aGracefulDieEventWithoutAPrecedingStopIsNotReportedAsFailure() {
        // The STOPPING transition can be missed while the event stream is down; a clean exit must
        // not be mislabelled as a failure, because FAILED is terminal for clients.
        List<GameServerStatusUpdateEventType> received = recordStatusUpdates();
        handler.attachStatusSupplier(SERVER_UUID, () -> GameServerDto.GameServerStatus.RUNNING);

        currentCallback().onNext(containerEvent("die", "0"));

        assertThat(received).containsExactly(GameServerStatusUpdateEventType.STOPPED);
    }

    @Test
    void aFailingListenerNeitherKillsTheStreamNorHidesTheEventFromOtherListeners() {
        // An exception escaping the docker-java callback ends the subscription for good: this is
        // how a status write for an already deleted game server used to wedge the whole backend.
        handler.attachStatusListener(
                (type, uuid) -> {
                    throw new IllegalStateException("Unexpected row count (expected 1 but was 0)");
                });
        List<GameServerStatusUpdateEventType> received = recordStatusUpdates();
        ResultCallback<Event> callback = currentCallback();

        callback.onNext(containerEvent("start", null));
        callback.onNext(containerEvent("start", null));

        assertThat(received)
                .as("a healthy listener must keep receiving events")
                .containsExactly(
                        GameServerStatusUpdateEventType.STARTED,
                        GameServerStatusUpdateEventType.STARTED);
        sleep(NO_ACTION_WINDOW);
        assertThat(callbacks)
                .as("the subscription must survive, so no reconnect is needed")
                .hasSize(1);
    }

    @Test
    void aDieEventForADeletedServerDoesNotKillTheStream() {
        List<GameServerStatusUpdateEventType> received = recordStatusUpdates();
        // Looking up a deleted server throws; the die event of its container still arrives.
        handler.attachStatusSupplier(
                SERVER_UUID,
                () -> {
                    throw new IllegalStateException("Game server not found");
                });

        currentCallback().onNext(containerEvent("die", "143"));

        assertThat(received).containsExactly(GameServerStatusUpdateEventType.STOPPED);
        sleep(NO_ACTION_WINDOW);
        assertThat(callbacks).hasSize(1);
    }

    @Test
    void anUnexpectedDieEventIsReportedAsFailure() {
        List<GameServerStatusUpdateEventType> received = recordStatusUpdates();
        handler.attachStatusSupplier(SERVER_UUID, () -> GameServerDto.GameServerStatus.RUNNING);

        currentCallback().onNext(containerEvent("die", "1"));

        assertThat(received).containsExactly(GameServerStatusUpdateEventType.FAILED);
    }

    private List<GameServerStatusUpdateEventType> recordStatusUpdates() {
        List<GameServerStatusUpdateEventType> received = new ArrayList<>();
        handler.attachStatusListener((type, uuid) -> received.add(type));
        return received;
    }

    private Event containerEvent(String action, String exitCode) {
        Map<String, String> attributes =
                exitCode == null
                        ? Map.of("name", CONTAINER_PREFIX + SERVER_UUID)
                        : Map.of("name", CONTAINER_PREFIX + SERVER_UUID, "exitCode", exitCode);
        return new Event()
                .withAction(action)
                .withEventActor(new EventActor().withAttributes(attributes));
    }

    private ResultCallback<Event> currentCallback() {
        return callbacks.get(callbacks.size() - 1);
    }

    private void awaitSubscriptions(int expected) {
        long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
        while (callbacks.size() < expected && System.nanoTime() < deadline) {
            sleep(Duration.ofMillis(10));
        }
        assertThat(callbacks)
                .as("the event subscription must be re-established instead of dying silently")
                .hasSize(expected);
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private DockerContainerNameResolver containerNameResolver() {
        EngineProperties.Docker docker =
                new EngineProperties.Docker(null, null, false, null, null, CONTAINER_PREFIX, null);
        return new DockerContainerNameResolver(
                new EngineProperties(docker, new EngineProperties.Reconciliation(180000, 60000)));
    }

    /** Keeps the tests fast; the production backoff starts at one second. */
    private ReconnectBackoff immediateBackoff() {
        return new ReconnectBackoff(Duration.ZERO, Duration.ZERO, 0);
    }
}
