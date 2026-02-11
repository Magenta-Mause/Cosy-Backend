package com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import jakarta.persistence.*;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class GameServerAccessGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    private String groupName;

    @Enumerated(EnumType.STRING)
    @ElementCollection(fetch = FetchType.EAGER)
    private List<GameServerAccessPermission> permissions;

    @ManyToMany
    @JoinTable(
            name = "access_group_users",
            joinColumns = @JoinColumn(name = "access_group_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private List<UserEntity> users;

    @ManyToOne
    @JoinColumn(name = "game_server_id")
    private GameServerEntity gameServer;
}
