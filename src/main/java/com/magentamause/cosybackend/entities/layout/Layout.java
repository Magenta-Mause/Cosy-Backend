package com.magentamause.cosybackend.entities.layout;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Inheritance(strategy = InheritanceType.JOINED)
public class Layout {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Size size;
}
