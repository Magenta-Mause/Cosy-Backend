package com.magentamause.cosybackend.services.engine.docker.util;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;

public class DockerStatsMapper {

    public static GameServerDto.GameServerStatus mapDockerStateToGameServerStatus(
            String dockerState) {
        return switch (dockerState != null ? dockerState.toLowerCase() : "") {
            case "running" -> GameServerDto.GameServerStatus.RUNNING;
            default -> GameServerDto.GameServerStatus.STOPPED;
        };
    }
}
