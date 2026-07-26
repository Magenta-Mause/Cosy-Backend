package com.magentamause.cosybackend.security.jwtfilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.services.user.UserEntityService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    private static final String USER_UUID = "11111111-2222-3333-4444-555555555555";

    @Mock private JwtUtils jwtUtils;
    @Mock private UserEntityService userEntityService;
    @Mock private FilterChain filterChain;
    @Mock private Claims claims;

    @InjectMocks private JwtFilter jwtFilter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesFromTheAuthorizationHeader() throws Exception {
        UserEntity user = UserEntity.builder().uuid(USER_UUID).role(UserEntity.Role.OWNER).build();
        when(jwtUtils.getTokenContentBody("valid-token", JwtTokenBody.TokenType.IDENTITY_TOKEN))
                .thenReturn(claims);
        when(claims.getSubject()).thenReturn(USER_UUID);
        when(userEntityService.getUserByUuid(USER_UUID)).thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/game-server");
        request.addHeader("Authorization", "Bearer valid-token");

        jwtFilter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOf(AuthenticationToken.class)
                .extracting(authentication -> ((AuthenticationToken) authentication).getUserId())
                .isEqualTo(USER_UUID);
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void ignoresATokenPassedAsARequestParameter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/game-server");
        request.setParameter("authToken", "valid-token");

        jwtFilter.doFilter(request, new MockHttpServletResponse(), filterChain);

        // The request continues unauthenticated: the chain ends in .authenticated(), so
        // Spring Security rejects it. What must not happen is the token being honoured.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtUtils, never()).getTokenContentBody(anyString(), any());
        verify(filterChain).doFilter(any(), any());
    }
}
