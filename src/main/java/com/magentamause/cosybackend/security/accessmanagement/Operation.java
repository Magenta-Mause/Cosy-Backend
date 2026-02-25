package com.magentamause.cosybackend.security.accessmanagement;

/**
 * Defines all authorization operations. Each operation represents a specific action that can be
 * performed on a resource.
 */
public enum Operation {
    // User operations
    USER_GET_ALL,
    USER_GET_BY_UUID,
    USER_GET_BY_USERNAME,
    USER_UPDATE,
    USER_CHANGE_PASSWORD,
    USER_CHANGE_PASSWORD_BY_ADMIN,
    USER_UPDATE_DOCKER_LIMITS,
    USER_CHANGE_ROLE,
    USER_DELETE,
    USER_READ_PERMISSIONS,

    // Game Server operations
    GAME_SERVER_CREATE,
    GAME_SERVER_DELETE,
    GAME_SERVER_START_STOP,
    GAME_SERVER_UPDATE,
    GAME_SERVER_GET,
    GAME_SERVER_GET_ALL,
    GAME_SERVER_SEND_COMMAND,
    GAME_SERVER_GET_LOGS,
    GAME_SERVER_TRANSFER_OWNERSHIP,

    // Game Server Configuration operations
    GAME_SERVER_METRIC_CONFIG_CHANGE,
    GAME_SERVER_PRIVATE_DASHBOARD_CONFIG_CHANGE,
    GAME_SERVER_PERMISSIONS_CONFIG_CHANGE,
    GAME_SERVER_RCON_CONFIG_CHANGE,

    // Game Server Files operations
    GAME_SERVER_FILES_READ,
    GAME_SERVER_FILES_UPDATE,

    // Game Server Log operations
    GAME_SERVER_LOG_READ,

    // Game Server Webhook operations
    GAME_SERVER_WEBHOOK_CREATE,
    GAME_SERVER_WEBHOOK_READ,
    GAME_SERVER_WEBHOOK_UPDATE,
    GAME_SERVER_WEBHOOK_DELETE,

    // Game Server Metric operations
    GAME_SERVER_METRIC_READ,

    // User Invite operations
    USER_INVITE_READ,
    USER_INVITE_CREATE,
    USER_INVITE_DELETE,

    // Footer operations
    FOOTER_UPDATE
}
