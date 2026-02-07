package com.magentamause.cosybackend.entities.layout;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Layout {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Size size;
}
