package com.magentamause.cosybackend.services.core.gameserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.PortMapping;
import com.magentamause.cosybackend.exceptions.PortInUseException;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.repositories.projections.GameServerPortUsage;
import com.magentamause.cosybackend.services.engine.EngineManager;
import com.magentamause.cosybackend.services.engine.PublishedPort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

/**
 * Two servers cannot publish the same host port at the same time — the second container to start is
 * rejected by Docker with an error the user never sees in context. These tests pin down which
 * situations are a real collision and which only look like one.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameServerPortCheckerTest {

    private static final String SERVER_UUID = "server-uuid";
    private static final String OTHER_UUID = "other-uuid";

    @Mock private EngineManager engineManager;
    @Mock private GameServerRepository gameServerRepository;

    private GameServerPortChecker checker;

    @BeforeEach
    void setUp() {
        checker = new GameServerPortChecker(engineManager, gameServerRepository);
        givenPublishedPorts();
        givenStoredServers();
    }

    @Test
    void allowsAStartWhenNothingHoldsTheWantedPort() {
        GameServerEntity server = server(SERVER_UUID, tcp(25565));

        assertThatCode(() -> checker.assertPortsAvailable(server)).doesNotThrowAnyException();
    }

    @Test
    void blocksAStartWhenAForeignContainerHoldsThePort() {
        GameServerEntity server = server(SERVER_UUID, tcp(25565));
        givenPublishedPorts(published(25565, PortMapping.PortProtocol.TCP, "grafana", null));

        assertThatThrownBy(() -> checker.assertPortsAvailable(server))
                .isInstanceOf(PortInUseException.class)
                .hasMessageContaining("25565/TCP");
    }

    @Test
    void doesNotNameTheOccupantInTheMessageShownToTheCaller() {
        GameServerEntity server = server(SERVER_UUID, tcp(25565));
        givenStoredServers(startedServer(OTHER_UUID, "someone elses server", tcp(25565)));

        assertThatThrownBy(() -> checker.assertPortsAvailable(server))
                .isInstanceOf(PortInUseException.class)
                .hasMessageNotContaining("someone elses server");
    }

    @Test
    void blocksAStartWhenAnotherServerClaimedThePortButHasNoContainerYet() {
        GameServerEntity server = server(SERVER_UUID, tcp(25565));
        givenStoredServers(startedServer(OTHER_UUID, "pulling server", tcp(25565)));

        assertThatThrownBy(() -> checker.assertPortsAvailable(server))
                .isInstanceOf(PortInUseException.class);
    }

    @Test
    void allowsAStartWhenTheOtherServerUsingThePortIsStopped() {
        GameServerEntity server = server(SERVER_UUID, tcp(25565));
        givenStoredServers(stoppedServer(OTHER_UUID, "idle server", tcp(25565)));

        assertThatCode(() -> checker.assertPortsAvailable(server)).doesNotThrowAnyException();
    }

    @Test
    void allowsAStartWhenOnlyThisServersOwnLeftoverContainerHoldsThePort() {
        GameServerEntity server = server(SERVER_UUID, tcp(25565));
        givenPublishedPorts(
                published(25565, PortMapping.PortProtocol.TCP, "cosy-" + SERVER_UUID, SERVER_UUID));

        assertThatCode(() -> checker.assertPortsAvailable(server)).doesNotThrowAnyException();
    }

    @Test
    void treatsTheSamePortNumberOnAnotherProtocolAsFree() {
        GameServerEntity server = server(SERVER_UUID, tcp(25565));
        givenPublishedPorts(published(25565, PortMapping.PortProtocol.UDP, "voice-chat", null));

        assertThatCode(() -> checker.assertPortsAvailable(server)).doesNotThrowAnyException();
    }

    @Test
    void reportsEveryConflictingPortAtOnce() {
        GameServerEntity server = server(SERVER_UUID, tcp(25565), tcp(25575));
        givenPublishedPorts(
                published(25565, PortMapping.PortProtocol.TCP, "foreign", null),
                published(25575, PortMapping.PortProtocol.TCP, "foreign", null));

        assertThatThrownBy(() -> checker.assertPortsAvailable(server))
                .hasMessageContaining("25565/TCP")
                .hasMessageContaining("25575/TCP");
    }

    @Test
    void reportsAPortHeldByBothSourcesOnlyOnce() {
        GameServerEntity server = server(SERVER_UUID, tcp(25565));
        givenPublishedPorts(
                published(25565, PortMapping.PortProtocol.TCP, "cosy-" + OTHER_UUID, OTHER_UUID));
        givenStoredServers(startedServer(OTHER_UUID, "running server", tcp(25565)));

        assertThat(checker.findBlockingConflicts(server)).hasSize(1);
    }

    @Test
    void rejectsAConfigurationThatBindsTheSameHostPortTwice() {
        GameServerEntity server = server(SERVER_UUID, tcp(25565), tcp(25565));

        assertThatThrownBy(() -> checker.assertNoDuplicatePorts(server))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("25565/TCP");
    }

    @Test
    void acceptsTheSameHostPortBoundOncePerProtocol() {
        GameServerEntity server = server(SERVER_UUID, tcp(25565), udp(25565));

        assertThatCode(() -> checker.assertNoDuplicatePorts(server)).doesNotThrowAnyException();
    }

    @Test
    void reportsAnOverlapWithAStoppedServerAsAdviceRatherThanAConflict() {
        GameServerEntity server = server(SERVER_UUID, tcp(25565));
        givenStoredServers(stoppedServer(OTHER_UUID, "idle server", tcp(25565)));

        assertThat(checker.findBlockingConflicts(server)).isEmpty();
        assertThat(checker.findConfiguredOverlaps(server))
                .extracting(GameServerPortChecker.PortConflict::describePort)
                .containsExactly("25565/TCP");
    }

    @Test
    void findsNoOverlapForAServerThatPublishesNoPorts() {
        GameServerEntity server = server(SERVER_UUID);
        givenStoredServers(startedServer(OTHER_UUID, "running server", tcp(25565)));

        assertThat(checker.findConfiguredOverlaps(server)).isEmpty();
        assertThat(checker.findBlockingConflicts(server)).isEmpty();
    }

    private void givenPublishedPorts(PublishedPort... ports) {
        when(engineManager.getPublishedHostPorts()).thenReturn(List.of(ports));
    }

    private void givenStoredServers(GameServerPortUsage... usages) {
        when(gameServerRepository.findAllPortUsages()).thenReturn(List.of(usages));
    }

    private PublishedPort published(
            int port, PortMapping.PortProtocol protocol, String containerName, String uuid) {
        return new PublishedPort(port, protocol, containerName, uuid);
    }

    private GameServerEntity server(String uuid, PortMapping... mappings) {
        return GameServerEntity.builder()
                .uuid(uuid)
                .serverName("server")
                .status(GameServerDto.GameServerStatus.STOPPED)
                .portMappings(List.of(mappings))
                .build();
    }

    private GameServerPortUsage startedServer(String uuid, String name, PortMapping mapping) {
        return usage(uuid, name, GameServerDto.GameServerStatus.PULLING_IMAGE, mapping);
    }

    private GameServerPortUsage stoppedServer(String uuid, String name, PortMapping mapping) {
        return usage(uuid, name, GameServerDto.GameServerStatus.STOPPED, mapping);
    }

    private GameServerPortUsage usage(
            String uuid, String name, GameServerDto.GameServerStatus status, PortMapping mapping) {
        return new GameServerPortUsage(
                uuid, name, status, mapping.getInstancePort(), mapping.getProtocol());
    }

    private PortMapping tcp(int port) {
        return mapping(port, PortMapping.PortProtocol.TCP);
    }

    private PortMapping udp(int port) {
        return mapping(port, PortMapping.PortProtocol.UDP);
    }

    private PortMapping mapping(int port, PortMapping.PortProtocol protocol) {
        return PortMapping.builder()
                .instancePort(port)
                .containerPort(port)
                .protocol(protocol)
                .build();
    }
}
