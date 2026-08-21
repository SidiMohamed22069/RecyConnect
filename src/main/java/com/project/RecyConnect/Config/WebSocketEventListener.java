package com.project.RecyConnect.Config;

import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.UserRepo;
import com.project.RecyConnect.Service.UserSessionManager;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
public class WebSocketEventListener {
    
    private final UserSessionManager sessionManager;
    private final UserRepo userRepo;
    
    public WebSocketEventListener(UserSessionManager sessionManager, UserRepo userRepo) {
        this.sessionManager = sessionManager;
        this.userRepo = userRepo;
    }
    
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();
        
        if (principal != null) {
            String sessionId = headerAccessor.getSessionId();
            // Le principal contient le username (depuis JWT)
            String username = principal.getName();
            User user = userRepo.findByUsername(username);
            
            if (user != null) {
                sessionManager.addSession(user.getId(), sessionId);
            }
        }
    }
    
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();
        
        if (principal != null) {
            String sessionId = headerAccessor.getSessionId();
            String username = principal.getName();
            User user = userRepo.findByUsername(username);
            
            if (user != null) {
                sessionManager.removeSession(user.getId(), sessionId);
            }
        }
    }
}

