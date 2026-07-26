package com.magentamause.cosybackend.security;

import com.magentamause.cosybackend.security.jwtfilter.JwtFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF is disabled as this is a stateless JWT-based API: there is no ambient
                // credential for a cross-site request to ride. Authentication is a bearer JWT
                // the attacker cannot produce — normally the Authorization header, but JwtFilter
                // also accepts an "authToken" request parameter. (The /v1/ws handshake reads a
                // parameter of the same name, but through its own JwtHandshakeInterceptor; the
                // endpoint is permitAll() here, so it does not depend on JwtFilter's fallback.)
                // Neither form is attached by the browser on its own.
                // The only cookie is the refresh token: HttpOnly, SameSite=Strict,
                // path-scoped to /auth/token and read only by a GET, which Spring's CsrfFilter
                // exempts anyway.
                // Caveat: POST /auth/logout is permitAll() and takes no credential, so a
                // cross-site form post can force-clear the victim's refresh cookie. That is a
                // nuisance, not a compromise — nothing is read or written on their behalf.
                // See docs/conventions/AUTH.md; CodeQL alert #2 is dismissed on this basis.
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        authorizeRequests ->
                                authorizeRequests
                                        .requestMatchers(
                                                "/auth/**",
                                                "/user-invites/use/*",
                                                "/v3/api-docs/**",
                                                "/actuator/**",
                                                "/swagger-ui/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/game-server")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/game-server/*/logs")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET, "/game-server/*/metrics/public")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/user-invites/*")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/footer")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.PUT,
                                                "/internal/game-server/custom-metric/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/internal/game-server/test-connection/**")
                                        .permitAll()
                                        .requestMatchers("/v1/ws/**")
                                        .permitAll()
                                        .dispatcherTypeMatchers(DispatcherType.ASYNC)
                                        .permitAll() // Allow async dispatches to bypass
                                        // re-authentication (e.g. for streaming
                                        // responses)
                                        .requestMatchers("/**")
                                        .authenticated())
                .cors(Customizer.withDefaults())
                .sessionManagement(
                        sessionManagement ->
                                sessionManagement.sessionCreationPolicy(
                                        SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        org.springframework.security.web.authentication
                                .UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(
                        exceptionHandling ->
                                exceptionHandling.authenticationEntryPoint(
                                        (request, response, authException) ->
                                                response.sendError(
                                                        HttpServletResponse.SC_UNAUTHORIZED,
                                                        "Unauthorized: Please provide valid credentials")))
                .build();
    }
}
