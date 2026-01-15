package com.magentamause.cosybackend.dtos.entitydtos;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = StartEventDto.Heartbeat.class, name = "HEARTBEAT"),
    @JsonSubTypes.Type(value = StartEventDto.PullProgress.class, name = "PULL_PROGRESS"),
    @JsonSubTypes.Type(value = StartEventDto.Done.class, name = "DONE"),
    @JsonSubTypes.Type(value = StartEventDto.Error.class, name = "ERROR")
})
public sealed interface StartEventDto {

    Type getType();

    enum Type {
        HEARTBEAT,
        PULL_PROGRESS,
        DONE,
        ERROR
    }

    @Getter
    @NoArgsConstructor
    final class Heartbeat implements StartEventDto {
        private final Type type = Type.HEARTBEAT;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    final class PullProgress implements StartEventDto {
        private final Type type = Type.PULL_PROGRESS;
        private PullProgressDto progress;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    final class Done implements StartEventDto {
        private final Type type = Type.DONE;
        private GameServerInstanceDto gameServerInstance;

        public static Done fromPorts(List<Integer> ports) {
            return new Done(new GameServerInstanceDto(ports));
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    final class Error implements StartEventDto {
        private final Type type = Type.ERROR;
        private String message;
    }
}
