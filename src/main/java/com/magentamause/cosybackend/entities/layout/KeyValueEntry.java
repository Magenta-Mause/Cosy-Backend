package com.magentamause.cosybackend.entities.layout;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class KeyValueEntry {
    private String Key;
    private String Value;
}
