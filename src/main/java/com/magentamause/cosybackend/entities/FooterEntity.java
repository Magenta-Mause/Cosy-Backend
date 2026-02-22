package com.magentamause.cosybackend.entities;

import com.magentamause.cosybackend.dtos.entitydtos.FooterDto;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class FooterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String street;

    @Column(nullable = false)
    private String city;

    public FooterDto toDto() {
        return FooterDto.builder()
                .id(this.id)
                .fullName(this.fullName)
                .email(this.email)
                .phone(this.phone)
                .street(this.street)
                .city(this.city)
                .build();
    }
}
