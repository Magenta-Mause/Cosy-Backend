package com.magentamause.cosybackend.configs.websockets;

import com.magentamause.cosybackend.configs.properties.CorsProperties;
import com.magentamause.cosybackend.security.websocket.JwtChannelInterceptor;
import com.magentamause.cosybackend.security.websocket.JwtHandshakeHandler;
import com.magentamause.cosybackend.security.websocket.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@EnableConfigurationProperties(CorsProperties.class)
public class WebsocketConfig implements WebSocketMessageBrokerConfigurer {

    private final CorsProperties corsProperties;
    private final JwtHandshakeHandler jwtHandshakeHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final JwtChannelInterceptor jwtChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker(WebSocketDestinations.BROKER_PREFIX);
        config.setApplicationDestinationPrefixes(WebSocketDestinations.APP_PREFIX);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(WebSocketDestinations.ENDPOINT)
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(new String[0]))
                .setHandshakeHandler(jwtHandshakeHandler)
                .addInterceptors(jwtHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }
}
