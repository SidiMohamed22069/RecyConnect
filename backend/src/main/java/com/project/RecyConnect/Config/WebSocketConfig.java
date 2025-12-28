package com.project.RecyConnect.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    private final WebSocketAuthInterceptor authInterceptor;
    
    public WebSocketConfig(WebSocketAuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Active un simple broker pour envoyer des messages aux clients
        config.enableSimpleBroker("/user");
        // Préfixe pour les messages destinés aux méthodes annotées @MessageMapping
        config.setApplicationDestinationPrefixes("/app");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint pour se connecter au WebSocket
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // En production, spécifiez vos domaines
                .withSockJS();
    }
    
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Temporairement désactivé pour éviter la boucle infinie
        // TODO: Réactiver une fois le problème résolu
        // registration.interceptors(authInterceptor);
    }
}

