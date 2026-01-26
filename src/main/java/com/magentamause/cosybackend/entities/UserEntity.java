package com.magentamause.cosybackend.entities;

import com.magentamause.cosybackend.dtos.entitydtos.UserEntityDto;
import com.magentamause.cosybackend.entities.utility.DockerHardwareLimits;
import jakarta.persistence.*;
import java.util.List;
import lombok.*;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"password", "invites", "gameServerConfigurationEntities", "startedServers"})
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private boolean defaultPasswordReset;

    @Enumerated(EnumType.STRING)
    private Role role;

    @OneToMany(mappedBy = "invitedBy", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserInviteEntity> invites;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameServerEntity> gameServerConfigurationEntities;

    @OneToMany(mappedBy = "lastStartedBy")
    private List<GameServerEntity> startedServers;

    @Embedded private DockerHardwareLimits dockerHardwareLimits;

    @Getter
    @RequiredArgsConstructor
    public enum Role {
        OWNER(true),
        ADMIN(true),
        QUOTA_USER(false);

        private final boolean admin;
    }

    public UserEntityDto toDto() {
        return UserEntityDto.builder()
                .uuid(this.uuid)
                .username(this.username)
                .role(this.role)
                .dockerHardwareLimits(this.dockerHardwareLimits)
                .build();
    }
}
