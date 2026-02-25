package com.magentamause.cosybackend.entities.gameserver;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.entities.PublicDashboard;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.WebhookEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.*;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessGroupEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.entities.layout.MetricLayout;
import com.magentamause.cosybackend.entities.layout.PrivateDashboardLayout;
import com.magentamause.cosybackend.security.accessmanagement.policies.GameServerFieldVisibilityPolicy;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@ToString(exclude = {"owner", "game"})
public class GameServerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    private String serverName;

    @ManyToOne
    private UserEntity owner;

    @Enumerated(EnumType.STRING)
    private GameServerDto.GameServerStatus status;

    @Enumerated(EnumType.STRING)
    private GameServerDesign design;

    @CreationTimestamp
    private LocalDateTime createdOn;

    private LocalDateTime timestampLastStarted;

    @ManyToOne
    private GameEntity game;

    @Column(nullable = false)
    private String dockerImageName;

    private String dockerImageTag;

    private String containerSecret;

    @Column(columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> customMetricHolder = new HashMap<>();

    @Embedded
    private DockerHardwareLimits dockerHardwareLimits;

    @Embedded
    private RCONConfiguration rconConfiguration;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "docker_execution_command",
            joinColumns = @JoinColumn(name = "game_server_configuration_uuid"))
    @Column(name = "command_part")
    private List<String> dockerExecutionCommand;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "port_mappings",
            joinColumns = @JoinColumn(name = "game_server_configuration_uuid"))
    private List<PortMapping> portMappings;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "environment_variables",
            joinColumns = @JoinColumn(name = "game_server_configuration_uuid"))
    private List<EnvironmentVariableConfiguration> environmentVariables;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "game_server_configuration_uuid")
    private List<VolumeMountConfiguration> volumeMounts;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "metric_layout_uuid")
    @OrderColumn(name = "metric_layout_index")
    private List<MetricLayout> metricLayout;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "private_dashboard_layout_uuid")
    @OrderColumn(name = "private_dashboard_layout_index")
    private List<PrivateDashboardLayout> privateDashboardLayouts;

    @OneToMany(
            mappedBy = "gameServer",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<GameServerAccessGroupEntity> accessGroups;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "game_server_uuid")
    private List<WebhookEntity> webhooks;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "public_dashboard_uuid")
    private PublicDashboard publicDashboard;

    public GameServerDto toDto() {
        return GameServerDto.builder()
                .uuid(this.getUuid())
                .serverName(this.getServerName())
                .owner(Optional.ofNullable(this.getOwner()).map(UserEntity::toDto).orElse(null))
                .status(this.getStatus())
                .design(this.getDesign())
                .createdOn(this.getCreatedOn())
                .timestampLastStarted(this.getTimestampLastStarted())
                .gameUuid(Optional.ofNullable(this.getGame()).map(GameEntity::getUuid).orElse(null))
                .rconConfiguration(this.getRconConfiguration())
                .dockerImageName(this.getDockerImageName())
                .dockerImageTag(this.getDockerImageTag())
                .dockerHardwareLimits(this.getDockerHardwareLimits())
                .privateDashboardLayouts(this.getPrivateDashboardLayouts())
                .executionCommand(this.getDockerExecutionCommand())
                .portMappings(this.getPortMappings())
                .environmentVariables(this.getEnvironmentVariables())
                .volumeMounts(this.getVolumeMounts())
                .metricLayout(this.getMetricLayout())
                .publicDashboard(this.getPublicDashboard())
                .accessGroups(
                        Optional.ofNullable(this.getAccessGroups())
                                .map(
                                        access ->
                                                access.stream()
                                                        .map(GameServerAccessGroupEntity::toDto)
                                                        .toList())
                                .orElse(null))
                .webhooks(
                        Optional.ofNullable(this.getWebhooks())
                                .map(
                                        webhooks ->
                                                webhooks.stream()
                                                        .map(WebhookEntity::toDto)
                                                        .toList())
                                .orElse(null))
                .build();
    }

    public GameServerDto toPublicDto() {
        return GameServerDto.builder()
                .uuid(this.getUuid())
                .gameUuid(Optional.ofNullable(this.getGame()).map(GameEntity::getUuid).orElse(null))
                .serverName(this.getServerName())
                .status(this.getStatus())
                .owner(Optional.ofNullable(this.getOwner()).map(UserEntity::toDto).orElse(null))
                .design(this.getDesign())
                .publicDashboard(this.publicDashboard)
                .timestampLastStarted(this.getTimestampLastStarted())
                .createdOn(this.getCreatedOn())
                .build();
    }

    public GameServerDto toDto(UserEntity user) {
        List<GameServerAccessPermission> permissions =
                GameServerFieldVisibilityPolicy.resolvePermissions(this, user);
        return toDto(permissions);
    }

    public GameServerDto toDto(List<GameServerAccessPermission> permissions) {
        GameServerDto.GameServerDtoBuilder builder = this.toPublicDto().toBuilder();

        if (this.publicDashboard != null && (this.publicDashboard.isEnabled() || GameServerFieldVisibilityPolicy.canSeePublicDashboardConfigs(permissions))) {
            builder.publicDashboard(this.publicDashboard);
        } else {
            builder.publicDashboard(PublicDashboard.builder().build());
        }
        if (GameServerFieldVisibilityPolicy.canSeeServerConfigs(permissions)) {
            builder.dockerImageName(this.getDockerImageName())
                    .dockerImageTag(this.getDockerImageTag())
                    .dockerHardwareLimits(this.getDockerHardwareLimits())
                    .executionCommand(this.getDockerExecutionCommand())
                    .portMappings(this.getPortMappings())
                    .environmentVariables(this.getEnvironmentVariables())
                    .volumeMounts(this.getVolumeMounts());
        }
        if (GameServerFieldVisibilityPolicy.canSeeRconConfig(permissions)) {
            builder.rconConfiguration(this.getRconConfiguration());
        }
        if (GameServerFieldVisibilityPolicy.canSeeMetricLayout(permissions)) {
            builder.metricLayout(this.getMetricLayout());
        }
        if (GameServerFieldVisibilityPolicy.canSeePrivateDashboardLayout(permissions)) {
            builder.privateDashboardLayouts(this.getPrivateDashboardLayouts());
        }
        if (GameServerFieldVisibilityPolicy.canSeeAccessGroups(permissions)) {
            builder.accessGroups(
                    Optional.ofNullable(this.getAccessGroups())
                            .map(
                                    access ->
                                            access.stream()
                                                    .map(GameServerAccessGroupEntity::toDto)
                                                    .toList())
                            .orElse(null));
        }
        if (GameServerFieldVisibilityPolicy.canSeeWebhooks(permissions)) {
            builder.webhooks(
                    Optional.ofNullable(this.getWebhooks())
                            .map(webhooks -> webhooks.stream().map(WebhookEntity::toDto).toList())
                            .orElse(null));
        }
        return builder.build();
    }
}
