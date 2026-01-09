package com.magentamause.cosybackend.security.jwtfilter;

import com.magentamause.cosybackend.security.config.JwtProperties;
import io.jsonwebtoken.*;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtUtils {

    private final JwtParser jwtParser;
    private final JwtProperties jwtProperties;
    private final Key signingKey;

    public long getTokenValidityDuration(JwtTokenBody.TokenType tokenType) {
        return tokenType.equals(JwtTokenBody.TokenType.IDENTITY_TOKEN)
                ? jwtProperties.identityTokenExpirationTime()
                : jwtProperties.refreshTokenExpirationTime();
    }

    public String generateIdentityToken(Map<String, Object> claims, String username) {
        Map<String, Object> map = new HashMap<>(claims);
        map.put("tokenType", JwtTokenBody.TokenType.IDENTITY_TOKEN);
        return createToken(map, username, jwtProperties.identityTokenExpirationTime());
    }

    public String generateRefreshToken(Map<String, Object> claims, String username) {
        Map<String, Object> map = new HashMap<>(claims);
        map.put("tokenType", JwtTokenBody.TokenType.REFRESH_TOKEN);
        return createToken(map, username, jwtProperties.refreshTokenExpirationTime());
    }

    private String createToken(Map<String, Object> claims, String subject, long expirationTime) {
        return Jwts.builder()
                .issuer("cosy-backend")
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(signingKey)
                .compact();
    }

    public Claims getTokenContentBody(String token, JwtTokenBody.TokenType tokenType)
            throws SecurityException {
        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();

            if (!tokenType.toString().equals(claims.get("tokenType"))) {
                throw new SecurityException("Token type mismatch. Expected: " + tokenType);
            }

            if (!"cosy-backend".equals(claims.getIssuer())) {
                throw new SecurityException("Invalid token issuer");
            }

            if (claims.getSubject() == null || claims.getSubject().isEmpty()) {
                throw new SecurityException("Missing subject claim");
            }

            return claims;
        } catch (ExpiredJwtException e) {
            log.debug("Token expired: {}", e.getMessage());
            throw new SecurityException("Token expired");
        } catch (UnsupportedJwtException | MalformedJwtException e) {
            log.debug("Invalid token format: {}", e.getMessage());
            throw new SecurityException("Invalid token format");
        } catch (SecurityException e) {
            log.debug("Token validation failed: {}", e.getMessage());
            throw new SecurityException(e.getMessage());
        } catch (Exception e) {
            log.debug("Unexpected error while parsing JWT token", e);
            throw new SecurityException("Token validation failed");
        }
    }
}
