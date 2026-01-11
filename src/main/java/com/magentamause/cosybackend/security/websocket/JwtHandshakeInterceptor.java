package com.magentamause.cosybackend.security.websocket;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.jwtfilter.JwtTokenBody;
import com.magentamause.cosybackend.security.jwtfilter.JwtUtils;
import com.magentamause.cosybackend.services.UserEntityService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    private final UserEntityService userEntityService;
    private final JwtUtils jwtUtils;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes)
            throws Exception {
        try {

            if (request instanceof org.springframework.http.server.ServletServerHttpRequest) {
                HttpServletRequest servletRequest =
                        ((ServletServerHttpRequest) request).getServletRequest();
                String token = servletRequest.getParameter("authToken");
                if (token == null || token.isBlank()) {
                    throw new NoAuthException();
                }
                Claims jwtBody =
                        jwtUtils.getTokenContentBody(token, JwtTokenBody.TokenType.IDENTITY_TOKEN);
                Optional<UserEntity> userEntity =
                        userEntityService.getOptionalUserByUuid(jwtBody.getSubject());
                if (userEntity.isEmpty()) {
                    throw new NoAuthException();
                }
                attributes.put("userId", jwtBody.getSubject());
                attributes.put("user", userEntity.get());
                return true;
            }
            throw new NoAuthException();
        } catch (NoAuthException e) {
            attributes.put("unauthorized", true);
            return true;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {}

    static class NoAuthException extends RuntimeException {}
}
