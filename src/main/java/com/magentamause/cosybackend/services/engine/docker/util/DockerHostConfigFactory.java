package com.magentamause.cosybackend.services.engine.docker.util;

import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.services.engine.docker.DockerEngineManager;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class DockerHostConfigFactory {

    public HostConfig buildHostConfig(GameServerEntity serverConfig) {
        HostConfig hostConfig = HostConfig.newHostConfig();

        // add port bindings
        if (serverConfig.getPortMappings() != null && !serverConfig.getPortMappings().isEmpty()) {
            Ports portBindings = new Ports();
            serverConfig
                    .getPortMappings()
                    .forEach(
                            p -> {
                                ExposedPort exposed =
                                        DockerEngineManager.portMappingToExposedPort(p);
                                portBindings.bind(
                                        exposed, Ports.Binding.bindPort(p.getInstancePort()));
                            });
            hostConfig.withPortBindings(portBindings);
        }

        // add volume binds
        if (serverConfig.getVolumeMounts() != null && !serverConfig.getVolumeMounts().isEmpty()) {
            List<Bind> binds =
                    serverConfig.getVolumeMounts().stream()
                            .map(
                                    v ->
                                            new Bind(
                                                    v.getHostPath(),
                                                    new Volume(v.getContainerPath()),
                                                    AccessMode.rw))
                            .toList();
            hostConfig.withBinds(binds);
        }

        var limits = serverConfig.getDockerHardwareLimits();
        if (limits == null) {
            return hostConfig;
        }

        // memory limit
        if (limits.getDockerMemoryLimit() != null) {
            Long memoryBytes = MemoryUtils.parseMemoryStringToBytes(limits.getDockerMemoryLimit());
            hostConfig.withMemory(memoryBytes);
        }

        // add cpu limit
        if (limits.getDockerMaxCpuCores() != null) {
            Long nanoCpus = DockerCpuLimitMapper.toNanoCpu(limits.getDockerMaxCpuCores());
            hostConfig.withNanoCPUs(nanoCpus);
        }

        log.info("Host config: {}", hostConfig);

        return hostConfig;
    }
}
