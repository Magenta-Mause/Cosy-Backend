package com.magentamause.cosybackend.entities;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.utility.EnvironmentVariableConfiguration;
import com.magentamause.cosybackend.entities.utility.PortMapping;
import com.magentamause.cosybackend.entities.utility.VolumeMountConfiguration;
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
public class GameServerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    private String serverName;

    @ManyToOne
    private UserEntity owner;

    @Enumerated(EnumType.STRING)
    private GameServerDto.GameServerStatus status;

    private LocalDateTime timestampLastStarted;

    // No cascading or orphanRemoval, because GameEntities without a server can exist
    @ManyToOne
    private GameEntity game;

    @Column(nullable = false)
    private String dockerImageName;

    private String dockerImageTag;

    private String template;

    @ElementCollection
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

    @ElementCollection
    @CollectionTable(
            name = "environment_variables",
            joinColumns = @JoinColumn(name = "game_server_configuration_uuid"))
    private List<EnvironmentVariableConfiguration> environmentVariables;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "game_server_configuration_uuid")
    private List<VolumeMountConfiguration> volumeMounts;

    public GameServerDto toDto() {
        return GameServerDto.builder()
                .uuid(this.getUuid())
                .serverName(this.getServerName())
                .owner(Optional.ofNullable(this.getOwner()).map(UserEntity::toDto).orElse(null))
                .status(this.getStatus())
                .timestampLastStarted(this.getTimestampLastStarted())
                .gameUuid(this.getGame() == null ? null : this.getGame().getUuid())
                .dockerImageName(this.getDockerImageName())
                .dockerImageTag(this.getDockerImageTag())
                .template(this.getTemplate())
                .executionCommand(this.getDockerExecutionCommand())
                .portMappings(this.getPortMappings())
                .environmentVariables(this.getEnvironmentVariables())
                .volumeMounts(this.getVolumeMounts())
                .build();
    }
}
