package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.dtos.actiondtos.user.LoginDto;
import com.magentamause.cosybackend.security.jwtfilter.JwtTokenBody;
import com.magentamause.cosybackend.security.jwtfilter.JwtUtils;
import com.magentamause.cosybackend.services.auth.AuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthorizationController {
    private static final int MILLISECONDS_IN_SECOND = 1000;

    private final AuthorizationService authorizationService;
    private final JwtUtils jwtUtils;

    @Value("${server.servlet.context-path}")
    private String basePath;

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginDto loginDto,
            @RequestParam(value = "tokenMode", defaultValue = "cookie") String tokenMode) {
        String refreshToken =
                authorizationService.loginUser(loginDto.getUsername(), loginDto.getPassword());

        if ("direct".equals(tokenMode)) {
            return ResponseEntity.ok(Map.of("refreshToken", refreshToken));
        }

        ResponseCookie responseCookie =
                ResponseCookie.from("refreshToken", refreshToken)
                        .httpOnly(true)
                        .secure(false)
                        .maxAge(
                                jwtUtils.getTokenValidityDuration(
                                                JwtTokenBody.TokenType.REFRESH_TOKEN)
                                        / MILLISECONDS_IN_SECOND)
                        .path(basePath + "/auth/token")
                        .sameSite("Strict")
                        .build();
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .build();
    }

    @GetMapping("/token")
    public ResponseEntity<String> fetchToken(
            @CookieValue(value = "refreshToken") String refreshToken) {
        return ResponseEntity.ok(
                authorizationService.fetchIdentityTokenFromRefreshToken(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie deleteCookie =
                ResponseCookie.from("refreshToken", "")
                        .httpOnly(true)
                        .secure(false)
                        .path(basePath + "/auth/token")
                        .maxAge(0)
                        .sameSite("Strict")
                        .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .build();
    }
}
