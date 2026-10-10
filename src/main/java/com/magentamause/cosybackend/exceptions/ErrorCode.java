package com.magentamause.cosybackend.exceptions;

import org.springframework.http.HttpStatusCode;

/**
 * Machine-readable reason of a failed request, sent as {@code error_code} in the {@code
 * ApiResponse} envelope. Clients translate these; the human-readable text in {@code data} stays
 * English. Codes are part of the API contract: add new ones freely, never rename or reuse one.
 */
public enum ErrorCode {
    // Fallbacks derived from the HTTP status when no specific code was given
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    METHOD_NOT_ALLOWED,
    CONFLICT,
    PAYLOAD_TOO_LARGE,
    UPSTREAM_ERROR,
    INTERNAL_ERROR,

    VALIDATION_FAILED,

    // Auth & users
    INVALID_CREDENTIALS,
    OLD_PASSWORD_INCORRECT,
    INSUFFICIENT_PERMISSIONS,
    USER_NOT_FOUND,
    USERNAME_TAKEN,
    INVITE_NOT_FOUND,
    INVITE_USERNAME_TAKEN,

    // Game servers
    GAME_SERVER_NOT_FOUND,
    SERVER_ALREADY_STARTING,
    SERVER_NOT_STOPPED,
    SERVER_RUNNING,
    HOST_MOUNT_ADMIN_ONLY,
    HARDWARE_QUOTA_EXCEEDED,
    SERVER_RESOURCE_LIMIT_MISSING,
    HARDWARE_LIMIT_REQUIRED,
    ACCESS_GROUP_NOT_FOUND,
    ACCESS_GROUP_NAME_TAKEN;

    public static ErrorCode fromStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> CONFLICT;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 502, 503, 504 -> UPSTREAM_ERROR;
            default -> status.is4xxClientError() ? BAD_REQUEST : INTERNAL_ERROR;
        };
    }
}
