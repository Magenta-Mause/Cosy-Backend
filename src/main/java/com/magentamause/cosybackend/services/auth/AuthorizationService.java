package com.magentamause.cosybackend.services.auth;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.jwtfilter.JwtTokenBody;
import com.magentamause.cosybackend.security.jwtfilter.JwtUtils;
import com.magentamause.cosybackend.services.user.UserEntityService;
import io.jsonwebtoken.Claims;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final JwtUtils jwtUtils;
    private final UserEntityService userEntityService;
    private final PasswordEncoder passwordEncoder;

    public String loginUser(String username, String plainPassword) {
        UserEntity user;
        try {
            user = userEntityService.getUserByUsername(username);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        if (!passwordEncoder.matches(plainPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return generateRefreshToken(user.getUuid());
    }

    public String fetchIdentityTokenFromRefreshToken(String refreshToken) {
        Claims claims;
        try {
            claims =
                    jwtUtils.getTokenContentBody(
                            refreshToken, JwtTokenBody.TokenType.REFRESH_TOKEN);
        } catch (SecurityException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
        return generateIdentityToken(claims.getSubject());
    }

    public String generateIdentityToken(String userId) {
        return jwtUtils.generateIdentityToken(getClaimsFromUserId(userId), userId);
    }

    public String generateRefreshToken(String userId) {
        return jwtUtils.generateRefreshToken(getClaimsFromUserId(userId), userId);
    }

    private Map<String, Object> getClaimsFromUserId(String userId) {
        UserEntity user = userEntityService.getUserByUuid(userId);
        return Map.of(
                "username", user.getUsername(),
                "role", user.getRole());
    }
}
