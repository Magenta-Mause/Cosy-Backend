package com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement;

public enum GameServerAccessPermission {
    ADMIN,

    // Base
    SEE_SERVER,

    // File
    READ_SERVER_SERVER_FILES,
    CHANGE_SERVER_FILES,

    // Configs
    CHANGE_SERVER_CONFIGS,
    CHANGE_METRICS_SETTINGS,
    CHANGE_PERMISSIONS_SETTINGS,
    CHANGE_RCON_SETTINGS,


    // Server Actions
    START_STOP_SERVER,
    SEND_COMMANDS,

    // Logs/Metrics
    READ_SERVER_LOGS,
    READ_SERVER_METRICS,

    // Danger Zone
    TRANSFER_SERVER_OWNERSHIP,
    DELETE_SERVER;
}
