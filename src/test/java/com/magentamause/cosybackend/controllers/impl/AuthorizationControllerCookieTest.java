package com.magentamause.cosybackend.controllers.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.magentamause.cosybackend.configs.properties.SecurityProperties;
import com.magentamause.cosybackend.controllers.TokenMode;
import com.magentamause.cosybackend.dtos.actiondtos.user.LoginDto;
import com.magentamause.cosybackend.security.jwtfilter.JwtUtils;
import com.magentamause.cosybackend.services.auth.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthorizationControllerCookieTest {

    @Mock private AuthorizationService authorizationService;
    @Mock private JwtUtils jwtUtils;

    private AuthorizationController controllerWithCookieSecure(boolean cookieSecure) {
        AuthorizationController controller =
                new AuthorizationController(
                        authorizationService, jwtUtils, new SecurityProperties(cookieSecure));
        ReflectionTestUtils.setField(controller, "basePath", "/api");
        return controller;
    }

    private String loginSetCookie(boolean cookieSecure) {
        when(authorizationService.loginUser(any(), any())).thenReturn("refresh-token");
        when(jwtUtils.getTokenValidityDuration(any())).thenReturn(2678400000L);

        LoginDto loginDto = new LoginDto();
        loginDto.setUsername("admin");
        loginDto.setPassword("admin");

        return controllerWithCookieSecure(cookieSecure)
                .login(loginDto, TokenMode.COOKIE)
                .getHeaders()
                .getFirst(HttpHeaders.SET_COOKIE);
    }

    @Test
    void omitsSecureByDefaultSoPlainHttpInstallsCanStillLogIn() {
        assertThat(loginSetCookie(false)).doesNotContain("Secure").contains("HttpOnly");
    }

    @Test
    void marksTheRefreshCookieSecureWhenConfigured() {
        assertThat(loginSetCookie(true)).contains("Secure");
    }

    @Test
    void logoutCookieMatchesTheLoginCookieAttributes() {
        // A Set-Cookie whose attributes differ from the stored cookie's does not overwrite
        // it, so a Secure mismatch between login and logout would leave the user logged in.
        String logoutCookie =
                controllerWithCookieSecure(true)
                        .logout()
                        .getHeaders()
                        .getFirst(HttpHeaders.SET_COOKIE);

        assertThat(logoutCookie)
                .contains("Secure")
                .contains("HttpOnly")
                .contains("Path=/api/auth/token")
                .contains("Max-Age=0");
    }
}
