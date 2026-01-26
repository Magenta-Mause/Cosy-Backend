package com.magentamause.cosybackend.entities.utility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class PortMapping {
    @Min(1)
    @Max(65535)
    private int instancePort;

    @Min(1)
    @Max(65535)
    private int containerPort;

    @Enumerated(EnumType.STRING)
    @NonNull
    private PortProtocol protocol;

    public enum PortProtocol {
        TCP,
        UDP
    }
}
