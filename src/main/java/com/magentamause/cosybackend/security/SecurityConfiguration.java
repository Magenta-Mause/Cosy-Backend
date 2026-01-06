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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF is disabled as this is a stateless JWT-based API
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
                                        .requestMatchers(HttpMethod.GET, "/user-invites/*")
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
