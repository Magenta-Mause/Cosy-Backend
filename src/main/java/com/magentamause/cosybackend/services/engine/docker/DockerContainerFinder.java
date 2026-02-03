package com.magentamause.cosybackend.services.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.services.engine.docker.util.DockerContainerNameResolver;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Service for finding Docker containers by UUID or GameServerEntity. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerContainerFinder {

    private final DockerClient client;

    public Optional<Container> findContainer(GameServerEntity serverConfig) {
        return findContainer(serverConfig.getUuid());
    }

    public Optional<Container> findContainer(String serverUuid) {
        String nameToMatch = DockerContainerNameResolver.containerNameWithSlash(serverUuid);

        return client.listContainersCmd().withShowAll(true).exec().stream()
                .filter(
                        c ->
                                Arrays.asList(
                                                Optional.ofNullable(c.getNames())
                                                        .orElse(new String[0]))
                                        .contains(nameToMatch))
                .findFirst();
    }
}
