package com.magentamause.cosybackend.configs.globalresponse;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.magentamause.cosybackend.exceptions.GamesApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<?>> handleResponseStatusException(
            ResponseStatusException ex, HttpServletRequest request) {
        log.warn("Response status exception occurred", ex);
        return ResponseEntity.status(ex.getStatusCode())
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .data(ex.getReason())
                                .error(ex.getMessage())
                                .path(request.getRequestURI())
                                .statusCode(ex.getStatusCode().value())
                                .build());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleResourceNotFound(NoResourceFoundException ex) {
        log.warn("Resource not found: \"{}\"", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .data("Resource not found")
                                .error(
                                        "404 No Resource found under \""
                                                + ex.getResourcePath()
                                                + '"')
                                .path(ex.getResourcePath())
                                .statusCode(HttpStatus.NOT_FOUND.value())
                                .build());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String path = request.getRequestURI();
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .data("HTTP method not supported")
                                .error("HTTP method not supported for this endpoint.")
                                .path(path)
                                .statusCode(HttpStatus.METHOD_NOT_ALLOWED.value())
                                .build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        String path = request.getRequestURI();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .data("Malformed JSON request")
                                .error("This endpoint expects a different request body.")
                                .path(path)
                                .statusCode(HttpStatus.BAD_REQUEST.value())
                                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors =
                ex.getBindingResult().getFieldErrors().stream()
                        .collect(
                                Collectors.toMap(
                                        fieldError ->
                                                PropertyNamingStrategies.SnakeCaseStrategy.INSTANCE
                                                        .translate(fieldError.getField()),
                                        fieldError -> {
                                            String defaultMessage = fieldError.getDefaultMessage();
                                            return defaultMessage != null
                                                    ? defaultMessage
                                                    : "No error message available";
                                        },
                                        (existing, replacement) -> existing + "; " + replacement));

        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.<Map<String, String>>builder()
                                .success(false)
                                .path(request.getRequestURI())
                                .data(errors)
                                .statusCode(HttpStatus.BAD_REQUEST.value())
                                .error("Invalid method arguments")
                                .build());
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingRequestCookie(
            MissingRequestCookieException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiResponse.builder()
                                .path(request.getRequestURI())
                                .statusCode(HttpStatus.BAD_REQUEST.value())
                                .success(false)
                                .data("Required cookie is missing")
                                .error("Required cookie is missing.")
                                .build());
    }

    @ExceptionHandler(GamesApiError.class)
    public ResponseEntity<ApiResponse<?>> handleGamesApiError(
            GamesApiError ex, HttpServletRequest request) {
        log.warn("Games API error occurred", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .data("Failed to communicate with Games API.")
                                .error(ex.getMessage())
                                .path(request.getRequestURI())
                                .statusCode(HttpStatus.BAD_GATEWAY.value())
                                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handle(Exception ex, HttpServletRequest request)
            throws Exception {
        String path = request.getRequestURI();
        log.warn("Unexpected error occurred", ex);

        if (path.startsWith("/api/v3/api-docs") || path.startsWith("/api/swagger-ui")) {
            throw ex;
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .data("An unexpected error occurred.")
                                .path(path)
                                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .build());
    }
}
