package com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement;

import lombok.Data;

import java.util.List;

@Data
public class GameServerAccessSettings {
    private String name;
    private List<GameServerAccessPermissions> permissions;
}
