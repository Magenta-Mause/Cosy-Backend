package com.magentamause.cosybackend.entities.gameserver.utility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnvironmentVariableConfiguration {
    @Column(name = "env_key") // Required for h2 databases as 'key' is a reserved word
    @NotBlank
    private String key;

    @Column(name = "env_value")
    @NotBlank
    private String value;
}
