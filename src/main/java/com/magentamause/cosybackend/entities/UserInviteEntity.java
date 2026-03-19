package com.magentamause.cosybackend.entities;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.dtos.entitydtos.UserInviteDto;
import com.magentamause.cosybackend.entities.gameserver.utility.DockerHardwareLimits;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EntityListeners(AuditingEntityListener.class)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserInviteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    private String username;

    @Column(unique = true, nullable = false)
    private String secretKey;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne
    @JoinColumn(name = "invited_by_id")
    private UserEntity invitedBy;

    @Enumerated(EnumType.STRING)
    private UserEntity.Role role;

    @Embedded private DockerHardwareLimits dockerHardwareLimits;

    // Port restrictions
    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private boolean portRestrictionsEnabled = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_invite_allowed_ports",
            joinColumns = @JoinColumn(name = "user_invite_uuid"))
    @Column(name = "port_range")
    private List<String> allowedPorts;

    // Game server creation permission
    @Column(columnDefinition = "boolean default true")
    @Builder.Default
    private boolean allowGameServerCreation = true;

    // MC-Router domain restrictions
    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private boolean mcRouterAllowAllDomains = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_invite_mc_router_domains",
            joinColumns = @JoinColumn(name = "user_invite_uuid"))
    @Column(name = "domain")
    private List<String> mcRouterAllowedDomains;

    public UserInviteDto convertToDto() {
        return UserInviteDto.builder()
                .uuid(this.getUuid())
                .username(this.getUsername())
                .invitedBy(this.getInvitedBy() != null ? this.getInvitedBy().getUuid() : null)
                .secretKey(this.getSecretKey())
                .createdAt(this.getCreatedAt())
                .inviteByUsername(this.getInvitedBy().getUsername())
                .role(this.getRole())
                .dockerHardwareLimits(this.dockerHardwareLimits)
                .portRestrictionsEnabled(this.portRestrictionsEnabled)
                .allowedPorts(this.allowedPorts)
                .allowGameServerCreation(this.allowGameServerCreation)
                .mcRouterAllowAllDomains(this.mcRouterAllowAllDomains)
                .mcRouterAllowedDomains(this.mcRouterAllowedDomains)
                .build();
    }
}
