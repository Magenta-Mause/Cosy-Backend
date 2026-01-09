package com.magentamause.cosybackend.security.jwtfilter;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.services.UserEntityService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserEntityService userEntityService;

    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return request.getParameter("authToken");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }
        Claims tokenBody;
        try {
            tokenBody = jwtUtils.getTokenContentBody(token, JwtTokenBody.TokenType.IDENTITY_TOKEN);
        } catch (SecurityException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
            return;
        }

        String subject = tokenBody.getSubject();
        UserEntity userEntity;
        try {
            userEntity = userEntityService.getUserByUuid(subject);
        } catch (ResponseStatusException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
            return;
        }

        SecurityContextHolder.getContext()
                .setAuthentication(new AuthenticationToken(subject, userEntity));

        filterChain.doFilter(request, response);
    }
}
