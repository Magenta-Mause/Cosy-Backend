package com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement;

public enum GameServerAccessPermission {
    ADMIN,

    // Base
    SEE_SERVER,

    START_STOP_SERVER,

    // Logs/Metrics
    READ_SERVER_LOGS,
    READ_SERVER_METRICS,

    // File
    READ_SERVER_SERVER_FILES,
    CHANGE_SERVER_FILES,

    SEND_COMMANDS,

    // Configs
    CHANGE_SERVER_CONFIGS,
    CHANGE_METRICS_SETTINGS,
    CHANGE_PERMISSIONS_SETTINGS,
    CHANGE_RCON_SETTINGS,

    // Danger Zone
    TRANSFER_SERVER_OWNERSHIP,
    DELETE_SERVER;
}
