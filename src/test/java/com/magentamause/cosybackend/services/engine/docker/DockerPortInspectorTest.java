package com.magentamause.cosybackend.services.engine.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.magentamause.cosybackend.configs.properties.EngineProperties;
import com.magentamause.cosybackend.entities.gameserver.utility.PortMapping;
import com.magentamause.cosybackend.services.engine.PublishedPort;
import com.magentamause.cosybackend.services.engine.docker.util.DockerContainerNameResolver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The port check is only as good as its view of what Docker has bound, so this covers what that
 * view includes: foreign containers count, unpublished and non-IP-port bindings do not, and an
 * unreachable daemon must not take the whole start path down with it.
 *
 * <p>The docker-java client is mocked, so no Docker daemon is involved.
 */
class DockerPortInspectorTest {

    private static final String CONTAINER_PREFIX = "cosy-";
    private static final String SERVER_UUID = "server-uuid";

    private DockerClient client;
    private ListContainersCmd listContainersCmd;
    private DockerPortInspector inspector;

    @BeforeEach
    void setUp() {
        client = mock(DockerClient.class);
        listContainersCmd = mock(ListContainersCmd.class);
        when(client.listContainersCmd()).thenReturn(listContainersCmd);

        EngineProperties engineProperties =
                new EngineProperties(
                        new EngineProperties.Docker(
                                null, null, false, null, null, CONTAINER_PREFIX, null),
                        new EngineProperties.Reconciliation(180000, 60000));
        inspector =
                new DockerPortInspector(client, new DockerContainerNameResolver(engineProperties));
    }

    @Test
    void reportsAPortOfACosyManagedContainerWithItsGameServerUuid() {
        givenContainers(container(CONTAINER_PREFIX + SERVER_UUID, port(25565, 25565, "tcp")));

        assertThat(inspector.listPublishedHostPorts())
                .containsExactly(
                        new PublishedPort(
                                25565,
                                PortMapping.PortProtocol.TCP,
                                CONTAINER_PREFIX + SERVER_UUID,
                                SERVER_UUID));
    }

    @Test
    void reportsAPortOfAForeignContainerWithoutAGameServerUuid() {
        givenContainers(container("some-other-service", port(8080, 80, "tcp")));

        assertThat(inspector.listPublishedHostPorts())
                .containsExactly(
                        new PublishedPort(
                                8080, PortMapping.PortProtocol.TCP, "some-other-service", null));
    }

    @Test
    void keepsUdpAndTcpBindingsApart() {
        givenContainers(container("game", port(25565, 25565, "udp")));

        assertThat(inspector.listPublishedHostPorts())
                .extracting(PublishedPort::protocol)
                .containsExactly(PortMapping.PortProtocol.UDP);
    }

    @Test
    void ignoresAnExposedPortThatIsNotPublishedToTheHost() {
        ContainerPort unpublished = new ContainerPort().withPrivatePort(25565).withType("tcp");
        givenContainers(container("game", unpublished));

        assertThat(inspector.listPublishedHostPorts()).isEmpty();
    }

    @Test
    void ignoresAProtocolCosyCannotBind() {
        givenContainers(container("game", port(9000, 9000, "sctp")));

        assertThat(inspector.listPublishedHostPorts()).isEmpty();
    }

    @Test
    void returnsNoPortsWhenTheDaemonCannotBeReached() {
        when(listContainersCmd.exec()).thenThrow(new DockerException("daemon down", 500));

        assertThat(inspector.listPublishedHostPorts()).isEmpty();
    }

    private void givenContainers(Container... containers) {
        when(listContainersCmd.exec()).thenReturn(List.of(containers));
    }

    private Container container(String name, ContainerPort... ports) {
        Container container = mock(Container.class);
        when(container.getNames()).thenReturn(new String[] {"/" + name});
        when(container.getPorts()).thenReturn(ports);
        return container;
    }

    private ContainerPort port(int publicPort, int privatePort, String type) {
        return new ContainerPort()
                .withPublicPort(publicPort)
                .withPrivatePort(privatePort)
                .withType(type);
    }
}
