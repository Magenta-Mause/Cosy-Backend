package com.magentamause.cosybackend.services.technical;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.jwtfilter.JwtTokenBody;
import com.magentamause.cosybackend.security.jwtfilter.JwtUtils;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtUtils jwtUtils;

    public String buildRefreshToken(UserEntity user) {
        Map<String, Object> claims =
                Map.of(
                        "username",
                        user.getUsername(),
                        "tokenType",
                        JwtTokenBody.TokenType.REFRESH_TOKEN);

        return jwtUtils.generateRefreshToken(claims, user.getUsername());
    }

    public String buildIdentityToken(UserEntity user) {
        Map<String, Object> claims =
                Map.of(
                        "username", user.getUsername(),
                        "user",
                                UserEntity.builder()
                                        .uuid(user.getUuid())
                                        .username(user.getUsername())
                                        .build(),
                        "tokenType", JwtTokenBody.TokenType.IDENTITY_TOKEN);

        return jwtUtils.generateIdentityToken(claims, user.getUsername());
    }
}
