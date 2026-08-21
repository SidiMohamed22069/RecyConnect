package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.NotificationDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Service pour envoyer des notifications via WebSocket
 */
@Service
public class WebSocketService {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    
    public WebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Envoie une notification à un utilisateur spécifique via WebSocket
     */
    public void sendToUser(Long userId, NotificationDTO notification) {
        try {
            String destination = "/user/" + userId + "/notifications";
            messagingTemplate.convertAndSend(destination, notification);
        } catch (Exception e) {
            System.err.println("Erreur lors de l'envoi WebSocket: " + e.getMessage());
        }
    }
}

