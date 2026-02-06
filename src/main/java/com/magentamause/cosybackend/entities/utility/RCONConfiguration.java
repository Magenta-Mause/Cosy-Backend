package com.magentamause.cosybackend.entities.utility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.AssertTrue;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
public class RCONConfiguration {
    boolean enabled;
    Integer port;
    String password;

    @AssertTrue(message = "Password must not be empty when RCON is enabled")
    public boolean isPasswordValid() {
        return !Boolean.TRUE.equals(enabled) || (password != null && !password.isBlank());
    }

    @AssertTrue(message = "Port must be valid (1-65535) when RCON is enabled")
    public boolean isPortValid() {
        return !Boolean.TRUE.equals(enabled) || (port != null && port >= 1 && port <= 65535);
    }
}
