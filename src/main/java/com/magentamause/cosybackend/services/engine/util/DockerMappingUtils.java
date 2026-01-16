package com.magentamause.cosybackend.services.engine.util;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import org.springframework.stereotype.Component;

@Component
public class DockerMappingUtils {

    public GameServerDto.GameServerStatus mapDockerStateToGameServerStatus(String dockerState) {
        return switch (dockerState != null ? dockerState.toLowerCase() : "") {
            case "running" -> GameServerDto.GameServerStatus.RUNNING;
            default -> GameServerDto.GameServerStatus.STOPPED;
        };
    }

    public GameServerDto.GameServerStatus mapEventToStatus(String eventName) {
        if (eventName == null) {
            return null;
        }
        switch (eventName) {
            case "start":
            case "unpause":
                return GameServerDto.GameServerStatus.RUNNING;
            case "stop":
            case "destroy":
                return GameServerDto.GameServerStatus.STOPPED;
            case "pause":
                return GameServerDto.GameServerStatus.STOPPED;
            default:
                return null;
        }
    }
}
