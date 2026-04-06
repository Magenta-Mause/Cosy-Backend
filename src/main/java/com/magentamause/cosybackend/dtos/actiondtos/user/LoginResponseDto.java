package com.magentamause.cosybackend.dtos.actiondtos.user;

import lombok.Data;

@Data
public class LoginResponseDto {
    private final String refreshToken;
}
