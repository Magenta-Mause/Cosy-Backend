package com.magentamause.cosybackend.entities;

import com.magentamause.cosybackend.dtos.entitydtos.UserEntityDto;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.DockerHardwareLimits;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"password", "invites", "gameServerConfigurationEntities"})
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

    @Embedded private DockerHardwareLimits dockerHardwareLimits;

    // Port restrictions
    @Column(columnDefinition = "boolean default true")
    @Builder.Default
    private boolean portRestrictionsEnabled = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_allowed_ports", joinColumns = @JoinColumn(name = "user_uuid"))
    @Column(name = "port_range")
    @Builder.Default
    private List<String> allowedPorts = new ArrayList<>();

    // Game server creation permission
    @Column(columnDefinition = "boolean default true")
    @Builder.Default
    private boolean allowGameServerCreation = true;

    // MC-Router domain restrictions
    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private boolean mcRouterAllowAllDomains = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_mc_router_domains", joinColumns = @JoinColumn(name = "user_uuid"))
    @Column(name = "domain")
    @Builder.Default
    private List<String> mcRouterAllowedDomains = new ArrayList<>();

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
                .portRestrictionsEnabled(this.portRestrictionsEnabled)
                .allowedPorts(this.allowedPorts)
                .allowGameServerCreation(this.allowGameServerCreation)
                .mcRouterAllowAllDomains(this.mcRouterAllowAllDomains)
                .mcRouterAllowedDomains(this.mcRouterAllowedDomains)
                .build();
    }
}
