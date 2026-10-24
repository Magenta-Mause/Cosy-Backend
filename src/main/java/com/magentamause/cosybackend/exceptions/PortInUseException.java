package com.magentamause.cosybackend.exceptions;

import com.magentamause.cosybackend.services.core.gameserver.GameServerPortChecker.PortConflict;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A game server cannot start because at least one of its host ports is already bound.
 *
 * <p>The message deliberately names only the ports, never the occupying server or container: on a
 * multi-user instance the occupant may belong to a different user, and the caller has no business
 * learning that it exists. The occupant is logged server-side instead.
 */
@ResponseStatus(HttpStatus.CONFLICT)
@Getter
public class PortInUseException extends RuntimeException {

    private final transient List<PortConflict> conflicts;

    public PortInUseException(List<PortConflict> conflicts) {
        super(buildMessage(conflicts));
        this.conflicts = List.copyOf(conflicts);
    }

    private static String buildMessage(List<PortConflict> conflicts) {
        String ports =
                conflicts.stream()
                        .map(PortConflict::describePort)
                        .distinct()
                        .collect(Collectors.joining(", "));
        return "Port already in use on this host: "
                + ports
                + ". Stop whatever is using it, or configure a different port for this server.";
    }
}
