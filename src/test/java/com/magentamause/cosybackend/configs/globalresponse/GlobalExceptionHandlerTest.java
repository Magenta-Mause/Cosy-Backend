package com.magentamause.cosybackend.configs.globalresponse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magentamause.cosybackend.exceptions.ApiException;
import com.magentamause.cosybackend.exceptions.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request =
            new MockHttpServletRequest("POST", "/api/game-server/1/start");

    @Test
    void apiExceptionKeepsItsOwnErrorCode() {
        ResponseEntity<ApiResponse<?>> response =
                handler.handleResponseStatusException(
                        new ApiException(
                                HttpStatus.BAD_REQUEST,
                                ErrorCode.HARDWARE_QUOTA_EXCEEDED,
                                "Hardware quota limit reached"),
                        request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCode.HARDWARE_QUOTA_EXCEEDED);
        assertThat(response.getBody().getData()).isEqualTo("Hardware quota limit reached");
    }

    @Test
    void plainResponseStatusExceptionFallsBackToTheStatus() {
        ResponseEntity<ApiResponse<?>> response =
                handler.handleResponseStatusException(
                        new ResponseStatusException(HttpStatus.CONFLICT, "nope"), request);

        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void unexpectedExceptionIsAnInternalError() throws Exception {
        ResponseEntity<ApiResponse<?>> response =
                handler.handle(new IllegalStateException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void errorCodeIsSerializedInSnakeCase() throws Exception {
        String json =
                new ObjectMapper()
                        .findAndRegisterModules()
                        .writeValueAsString(
                                ApiResponse.builder().errorCode(ErrorCode.USERNAME_TAKEN).build());

        assertThat(json).contains("\"error_code\":\"USERNAME_TAKEN\"");
    }

    @Test
    void unmappedStatusesFallBackByClass() {
        assertThat(ErrorCode.fromStatus(HttpStatus.UNPROCESSABLE_ENTITY))
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ErrorCode.fromStatus(HttpStatus.NOT_IMPLEMENTED))
                .isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(ErrorCode.fromStatus(HttpStatus.PAYLOAD_TOO_LARGE))
                .isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);
    }
}
