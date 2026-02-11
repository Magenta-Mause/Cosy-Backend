package com.magentamause.cosybackend.entities.gameserver;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.*;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessGroup;
import com.magentamause.cosybackend.entities.layout.MetricLayout;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.*;

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

    @ManyToOne private UserEntity owner;

    @ManyToOne private UserEntity lastStartedBy;

    @Enumerated(EnumType.STRING)
    private GameServerDto.GameServerStatus status;

    private LocalDateTime timestampLastStarted;

    // No cascading or orphanRemoval, because GameEntities without a server can exist
    @ManyToOne private GameEntity game;

    @Column(nullable = false)
    private String dockerImageName;

    private String dockerImageTag;

    @Embedded private DockerHardwareLimits dockerHardwareLimits;

    @Embedded private RCONConfiguration rconConfiguration;

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
    private List<MetricLayout> metricLayout;

    @OneToMany(
            mappedBy = "gameServer",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<GameServerAccessGroup> accessGroups;

    public GameServerDto toDto() {
        return GameServerDto.builder()
                .uuid(this.getUuid())
                .serverName(this.getServerName())
                .owner(Optional.ofNullable(this.getOwner()).map(UserEntity::toDto).orElse(null))
                .status(this.getStatus())
                .timestampLastStarted(this.getTimestampLastStarted())
                .gameUuid(this.getGame() == null ? null : this.getGame().getUuid())
                .rconConfiguration(this.getRconConfiguration())
                .dockerImageName(this.getDockerImageName())
                .dockerImageTag(this.getDockerImageTag())
                .dockerHardwareLimits(this.getDockerHardwareLimits())
                .executionCommand(this.getDockerExecutionCommand())
                .portMappings(this.getPortMappings())
                .environmentVariables(this.getEnvironmentVariables())
                .volumeMounts(this.getVolumeMounts())
                .metricLayout(this.getMetricLayout())
                .build();
    }
}
