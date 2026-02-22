package com.magentamause.cosybackend.dtos.entitydtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FooterDto {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String street;
    private String city;
}
