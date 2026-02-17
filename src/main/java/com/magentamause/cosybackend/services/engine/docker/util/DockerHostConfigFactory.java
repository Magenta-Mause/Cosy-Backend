package com.magentamause.cosybackend.services.engine.docker.util;

import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.DockerHardwareLimits;
import com.magentamause.cosybackend.services.engine.docker.DockerVolumePathResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DockerHostConfigFactory {

    private final DockerVolumePathResolver volumePathResolver;

    public HostConfig buildHostConfig(GameServerEntity serverConfig) {
        HostConfig hostConfig = HostConfig.newHostConfig();

        applyPortBindings(hostConfig, serverConfig);
        applyVolumeBinds(hostConfig, serverConfig);
        applyHardwareLimits(hostConfig, serverConfig);

        hostConfig.withExtraHosts("host.docker.internal:host-gateway");

        log.debug("Host config: {}", hostConfig);

        return hostConfig;
    }

    private void applyPortBindings(HostConfig hostConfig, GameServerEntity serverConfig) {
        if (serverConfig.getPortMappings() != null && !serverConfig.getPortMappings().isEmpty()) {
            Ports portBindings = new Ports();
            serverConfig
                    .getPortMappings()
                    .forEach(
                            p -> {
                                ExposedPort exposed =
                                        DockerConfigurationMapper.portMappingToExposedPort(p);
                                portBindings.bind(
                                        exposed, Ports.Binding.bindPort(p.getInstancePort()));
                            });
            hostConfig.withPortBindings(portBindings);
        }
    }

    private void applyVolumeBinds(HostConfig hostConfig, GameServerEntity serverConfig) {
        if (serverConfig.getVolumeMounts() != null && !serverConfig.getVolumeMounts().isEmpty()) {
            List<Bind> binds =
                    serverConfig.getVolumeMounts().stream()
                            .map(
                                    v -> {
                                        String hostPath =
                                                volumePathResolver.resolveAndEnsureVolumeHostPath(
                                                        v);
                                        return new Bind(
                                                hostPath,
                                                new Volume(v.getContainerPath()),
                                                AccessMode.rw);
                                    })
                            .toList();
            hostConfig.withBinds(binds);
        }
    }

    private void applyHardwareLimits(HostConfig hostConfig, GameServerEntity serverConfig) {
        DockerHardwareLimits limits = serverConfig.getDockerHardwareLimits();
        if (limits == null) {
            return;
        }

        applyMemoryLimit(hostConfig, limits.getDockerMemoryLimit());
        applyCpuLimit(hostConfig, limits.getDockerMaxCpuCores());
    }

    private void applyMemoryLimit(HostConfig hostConfig, String memoryLimit) {
        if (memoryLimit == null) {
            return;
        }
        Long memoryBytes = MemoryUtils.parseMemoryStringToBytes(memoryLimit);
        hostConfig.withMemory(memoryBytes);
    }

    private void applyCpuLimit(HostConfig hostConfig, Float cpuLimit) {
        if (cpuLimit == null) {
            return;
        }
        Long nanoCpus = DockerCpuLimitMapper.toNanoCpu(cpuLimit);
        hostConfig.withNanoCPUs(nanoCpus);
    }
}
