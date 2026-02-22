package com.magentamause.cosybackend.dtos.actiondtos;

import com.magentamause.cosybackend.entities.FooterEntity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FooterUpdateDto {
    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Phone is required")
    private String phone;

    @NotBlank(message = "Street is required")
    private String street;

    @NotBlank(message = "City is required")
    private String city;


    public FooterEntity applyToEntity(FooterEntity entity) {
        entity.setFullName(this.fullName);
        entity.setEmail(this.email);
        entity.setPhone(this.phone);
        entity.setStreet(this.street);
        entity.setCity(this.city);
        return entity;
    }
}
