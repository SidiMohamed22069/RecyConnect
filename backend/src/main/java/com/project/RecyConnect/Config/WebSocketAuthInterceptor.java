package com.project.RecyConnect.Config;

import com.project.RecyConnect.Security.JwtUtil;
import com.project.RecyConnect.Service.UserService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Intercepteur pour authentifier les connexions WebSocket via JWT
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    
    private final JwtUtil jwtUtil;
    private final UserService userService;
    
    public WebSocketAuthInterceptor(JwtUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }
    
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        // Ne traiter QUE les commandes CONNECT, ignorer tous les autres messages
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message; // Laisser passer tous les autres messages sans traitement
        }
        
        // Traiter uniquement les connexions
        String authToken = accessor.getFirstNativeHeader("Authorization");
        
        if (authToken == null || !authToken.startsWith("Bearer ")) {
            System.err.println("Pas de token d'authentification WebSocket");
            return null; // Rejeter la connexion
        }
        
        String token = authToken.substring(7);
        
        try {
            // Vérifier si le token est expiré AVANT de charger l'utilisateur
            if (jwtUtil.isTokenExpired(token)) {
                System.err.println("Token WebSocket expiré");
                return null; // Rejeter la connexion
            }
            
            // Extraire le username du token
            String username = jwtUtil.extractEmail(token);
            
            // Charger les détails de l'utilisateur
            UserDetails userDetails = userService.loadUserByUsername(username);
            
            // Valider le token
            if (jwtUtil.validateToken(token, userDetails)) {
                // Créer un Principal avec le username
                Principal principal = new UsernamePasswordAuthenticationToken(
                    username, null, userDetails.getAuthorities()
                );
                accessor.setUser(principal);
            } else {
                System.err.println("Token WebSocket invalide");
                return null; // Rejeter la connexion
            }
        } catch (Exception e) {
            // Token invalide - la connexion sera rejetée
            System.err.println("Erreur d'authentification WebSocket: " + e.getMessage());
            return null; // Rejeter la connexion
        }
        
        return message;
    }
}

