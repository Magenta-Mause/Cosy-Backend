package com.magentamause.cosybackend.entities;

import com.magentamause.cosybackend.dtos.entitydtos.McRouterConfigurationDto;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Embeddable
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McRouterConfiguration {

    @Column(name = "mc_router_enabled")
    @Builder.Default
    private boolean enabled = false;

    @Column(name = "mc_router_port")
    @Builder.Default
    private int port = 25565;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "mc_router_domains",
            joinColumns = @JoinColumn(name = "cosy_instance_settings_id"))
    @Column(name = "domain")
    @Builder.Default
    private List<String> domains = new ArrayList<>();

    public McRouterConfigurationDto toDto() {
        return McRouterConfigurationDto.builder()
                .enabled(this.enabled)
                .port(this.port)
                .domains(this.domains != null ? new ArrayList<>(this.domains) : new ArrayList<>())
                .build();
    }
}
