package com.project.RecyConnect.Config;

import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.UserRepo;
import com.project.RecyConnect.Security.JwtUtil;
import com.project.RecyConnect.Service.UserService;
import lombok.extern.slf4j.Slf4j;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intercepteur pour authentifier les connexions WebSocket via JWT.
 *
 * Deux responsabilites:
 *  - CONNECT   : authentifier le client a partir du JWT et attacher un Principal.
 *  - SUBSCRIBE : verifier que le client ne s'abonne qu'a SON propre canal.
 *
 * Sans le controle sur SUBSCRIBE, le broker "/user" etant un simple topic,
 * n'importe quel client pourrait s'abonner a /user/{autreId}/notifications
 * et lire les notifications privees d'autrui.
 *
 * Les autres commandes STOMP (SEND, heartbeats, DISCONNECT...) sont laissees
 * passer telles quelles.
 */
@Slf4j
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    /** Destination des notifications personnelles: /user/{userId}/notifications */
    private static final Pattern USER_DESTINATION = Pattern.compile("^/user/(\\d+)/.*$");

    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final UserRepo userRepo;

    public WebSocketAuthInterceptor(JwtUtil jwtUtil, UserService userService, UserRepo userRepo) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.userRepo = userRepo;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        return switch (accessor.getCommand()) {
            case CONNECT -> authenticateConnect(accessor, message);
            case SUBSCRIBE -> authorizeSubscribe(accessor, message);
            // Toutes les autres commandes passent sans traitement supplementaire.
            default -> message;
        };
    }

    /**
     * Authentifie la commande CONNECT a partir du header Authorization.
     * @return le message si l'authentification reussit, null pour rejeter la connexion.
     */
    private Message<?> authenticateConnect(StompHeaderAccessor accessor, Message<?> message) {
        String authToken = accessor.getFirstNativeHeader("Authorization");

        if (authToken == null || !authToken.startsWith("Bearer ")) {
            log.warn("Connexion WebSocket refusee: aucun token fourni");
            return null;
        }

        String token = authToken.substring(7);

        try {
            if (jwtUtil.isTokenExpired(token)) {
                log.warn("Connexion WebSocket refusee: token expire");
                return null;
            }

            String username = jwtUtil.extractEmail(token);
            UserDetails userDetails = userService.loadUserByUsername(username);

            if (!jwtUtil.validateToken(token, userDetails)) {
                log.warn("Connexion WebSocket refusee: token invalide");
                return null;
            }

            Principal principal = new UsernamePasswordAuthenticationToken(
                    username, null, userDetails.getAuthorities());
            accessor.setUser(principal);
            return message;

        } catch (Exception e) {
            log.warn("Connexion WebSocket refusee: erreur d'authentification", e);
            return null;
        }
    }

    /**
     * N'autorise l'abonnement a /user/{userId}/** que si {userId} est bien
     * l'utilisateur authentifie sur cette session.
     * @return le message si l'abonnement est autorise, null pour le rejeter.
     */
    private Message<?> authorizeSubscribe(StompHeaderAccessor accessor, Message<?> message) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Matcher matcher = USER_DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            // Destination non personnelle: aucune regle de propriete a appliquer.
            return message;
        }

        Principal principal = accessor.getUser();
        if (principal == null) {
            log.warn("Abonnement refuse a {}: session non authentifiee", destination);
            return null;
        }

        User user = userRepo.findByUsername(principal.getName());
        if (user == null) {
            log.warn("Abonnement refuse a {}: utilisateur introuvable", destination);
            return null;
        }

        Long requestedUserId = Long.valueOf(matcher.group(1));
        if (!user.getId().equals(requestedUserId)) {
            log.warn("Abonnement refuse: l'utilisateur {} a tente d'ecouter le canal de {}",
                    user.getId(), requestedUserId);
            return null;
        }

        return message;
    }
}
