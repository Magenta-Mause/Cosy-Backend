package com.magentamause.cosybackend.configs.globalresponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.magentamause.cosybackend.entities.gameserver.utility.PortMapping;
import com.magentamause.cosybackend.exceptions.PortInUseException;
import com.magentamause.cosybackend.services.core.gameserver.GameServerPortChecker;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The catch-all {@code @ExceptionHandler(Exception.class)} answers anything it is asked about with
 * a generic 500, so an exception that relies only on {@code @ResponseStatus} silently loses both
 * its status and its message. This pins the port conflict's own handler in place.
 */
class PortInUseExceptionHandlingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void answersAPortConflictWithConflictAndTheOccupiedPorts() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/game-server/uuid/start");

        ResponseEntity<ApiResponse<?>> response =
                handler.handlePortInUse(
                        new PortInUseException(
                                List.of(
                                        new GameServerPortChecker.PortConflict(
                                                25565,
                                                PortMapping.PortProtocol.TCP,
                                                "container foreign"))),
                        request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiResponse<?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getStatusCode()).isEqualTo(HttpStatus.CONFLICT.value());
        // The frontend renders `data` as the error's details, so the port has to be in there.
        assertThat(body.getData()).asString().contains("25565/TCP");
        assertThat(body.getPath()).isEqualTo("/api/game-server/uuid/start");
    }
}
