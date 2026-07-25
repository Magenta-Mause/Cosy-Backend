package com.magentamause.cosybackend.services.engine.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.AttachContainerCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.magentamause.cosybackend.configs.properties.EngineProperties;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.services.engine.docker.util.DockerContainerNameResolver;
import com.magentamause.cosybackend.services.engine.docker.util.ReconnectBackoff;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the resilience contract of the container log attachment: a stream that ends must be
 * re-attached rather than silently lost, while a deliberate detach must stay detached.
 *
 * <p>The docker-java client is mocked, so no Docker daemon is involved.
 */
class DockerLogStreamerTest {

    private static final String CONTAINER_PREFIX = "cosy-";
    private static final String SERVER_UUID = "server-uuid";
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration NO_ACTION_WINDOW = Duration.ofMillis(300);

    private final List<ResultCallback<Frame>> callbacks = new CopyOnWriteArrayList<>();
    private final GameServerEntity server = GameServerEntity.builder().uuid(SERVER_UUID).build();

    private DockerContainerFinder containerFinder;
    private DockerLogStreamer streamer;

    @BeforeEach
    void setUp() {
        DockerClient client = mock(DockerClient.class);
        AttachContainerCmd attachCmd = mock(AttachContainerCmd.class, RETURNS_SELF);
        when(client.attachContainerCmd(anyString())).thenReturn(attachCmd);
        when(attachCmd.exec(any()))
                .thenAnswer(
                        invocation -> {
                            ResultCallback<Frame> callback = invocation.getArgument(0);
                            callbacks.add(callback);
                            return callback;
                        });

        containerFinder = mock(DockerContainerFinder.class);
        streamer =
                new DockerLogStreamer(
                        client, containerNameResolver(), containerFinder, immediateBackoff());
    }

    @AfterEach
    void tearDown() {
        streamer.cleanup();
    }

    @Test
    void reattachesWhenTheStreamFailsWhileTheContainerIsStillRunning() {
        givenContainerState("running");
        streamer.attachLogListener(server, message -> {});
        assertThat(callbacks).hasSize(1);

        callbacks.get(0).onError(new IOException("read timed out"));

        awaitAttachments(2);
        assertThat(streamer.isLogListenerAttached(SERVER_UUID)).isTrue();
    }

    @Test
    void reattachesWhenTheStreamCompletesWhileTheContainerIsStillRunning() {
        givenContainerState("running");
        streamer.attachLogListener(server, message -> {});

        callbacks.get(0).onComplete();

        awaitAttachments(2);
    }

    @Test
    void givesUpWhenTheContainerIsGone() {
        when(containerFinder.findContainer(any(GameServerEntity.class)))
                .thenReturn(Optional.empty());
        streamer.attachLogListener(server, message -> {});

        callbacks.get(0).onComplete();

        awaitDetached();
        sleep(NO_ACTION_WINDOW);
        assertThat(callbacks).hasSize(1);
    }

    @Test
    void aDeliberateDetachIsNotUndoneByTheStreamTermination() {
        givenContainerState("running");
        streamer.attachLogListener(server, message -> {});

        streamer.detachLogListener(SERVER_UUID);
        callbacks.get(0).onComplete();

        sleep(NO_ACTION_WINDOW);
        assertThat(callbacks).hasSize(1);
        assertThat(streamer.isLogListenerAttached(SERVER_UUID)).isFalse();
    }

    private void givenContainerState(String state) {
        Container container = mock(Container.class);
        when(container.getState()).thenReturn(state);
        when(containerFinder.findContainer(any(GameServerEntity.class)))
                .thenReturn(Optional.of(container));
    }

    private void awaitAttachments(int expected) {
        awaitCondition(() -> callbacks.size() >= expected);
        assertThat(callbacks)
                .as("the log stream must be re-attached instead of dying silently")
                .hasSize(expected);
    }

    private void awaitDetached() {
        awaitCondition(() -> !streamer.isLogListenerAttached(SERVER_UUID));
        assertThat(streamer.isLogListenerAttached(SERVER_UUID))
                .as("a vanished container must not be retried forever")
                .isFalse();
    }

    private void awaitCondition(BooleanSupplier condition) {
        long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            sleep(Duration.ofMillis(10));
        }
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
