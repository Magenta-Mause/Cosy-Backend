package com.magentamause.cosybackend.entities.layout;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Inheritance(strategy = InheritanceType.JOINED)
@AllArgsConstructor
@RequiredArgsConstructor
public class Layout {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Size size;
}
