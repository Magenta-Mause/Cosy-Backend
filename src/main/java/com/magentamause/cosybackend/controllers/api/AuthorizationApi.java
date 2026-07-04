package com.magentamause.cosybackend.controllers.api;

import com.magentamause.cosybackend.controllers.TokenMode;
import com.magentamause.cosybackend.dtos.actiondtos.user.LoginDto;
import com.magentamause.cosybackend.dtos.actiondtos.user.LoginResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Authorization", description = "Authentication and token management")
@RequestMapping("/auth")
public interface AuthorizationApi {

    @Operation(summary = "Login with username and password")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Logged in"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    ResponseEntity<LoginResponseDto> login(
            @Valid @RequestBody LoginDto loginDto,
            @RequestParam(value = "tokenMode", defaultValue = "COOKIE") TokenMode tokenMode);

    @Operation(summary = "Exchange refresh token cookie for identity token")
    @ApiResponse(responseCode = "200", description = "Identity token returned")
    @GetMapping("/token")
    ResponseEntity<String> fetchToken(@CookieValue(value = "refreshToken") String refreshToken);

    @Operation(summary = "Logout and clear refresh token cookie")
    @ApiResponse(responseCode = "204", description = "Logged out")
    @PostMapping("/logout")
    ResponseEntity<Void> logout();
}
