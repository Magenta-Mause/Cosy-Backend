package com.magentamause.cosybackend.dtos.entitydtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StartEventDto {

    public enum Type {
        HEARTBEAT,
        DONE,
        ERROR
    }

    private Type type;
    private List<Integer> ports;
    private String message; // for error info

    public static StartEventDto heartbeat() {
        return new StartEventDto(Type.HEARTBEAT, null, null);
    }

    public static StartEventDto done(List<Integer> ports) {
        return new StartEventDto(Type.DONE, ports, null);
    }

    public static StartEventDto error(String message) {
        return new StartEventDto(Type.ERROR, null, message);
    }
}
