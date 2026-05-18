package com.magentamause.cosybackend.services.auth;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.jwtfilter.JwtTokenBody;
import com.magentamause.cosybackend.security.jwtfilter.JwtUtils;
import com.magentamause.cosybackend.services.user.UserEntityService;
import io.jsonwebtoken.Claims;
import java.util.HashMap;
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
        return jwtUtils.generateIdentityToken(
                getClaimsFromUserId(userId, JwtTokenBody.TokenType.IDENTITY_TOKEN), userId);
    }

    public String generateRefreshToken(String userId) {
        return jwtUtils.generateRefreshToken(
                getClaimsFromUserId(userId, JwtTokenBody.TokenType.REFRESH_TOKEN), userId);
    }

    private Map<String, Object> getClaimsFromUserId(
            String userId, JwtTokenBody.TokenType tokenType) {
        UserEntity user = userEntityService.getUserByUuid(userId);

        Map<String, Object> userClaims =
                new HashMap<>(
                        Map.of(
                                "username", user.getUsername(),
                                "role", user.getRole()));

        if (tokenType == JwtTokenBody.TokenType.IDENTITY_TOKEN) {
            var limits = user.getDockerHardwareLimits();
            userClaims.put(
                    "cpu_cores_limit", limits != null ? limits.getDockerMaxCpuCores() : null);
            userClaims.put("memory_limit", limits != null ? limits.getDockerMemoryLimit() : null);
            userClaims.put("can_create_game_servers", user.getRole().isAdmin() || user.isCanCreateGameServers());
        }

        return userClaims;
    }
}
