package com.project.RecyConnect.Controller;

import com.project.RecyConnect.Service.NotificationService;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {
    
    private final NotificationService notificationService;
    
    public AdminNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }
    
    /**
     * Endpoint pour envoyer une notification broadcast à tous les utilisateurs
     * Seuls les admins peuvent utiliser cet endpoint
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/broadcast")
    public ResponseEntity<String> sendBroadcast(@RequestBody BroadcastNotificationDTO dto) {
        notificationService.sendBroadcastToAllUsers(dto.getTitle(), dto.getMessage());
        return ResponseEntity.ok("Notification envoyée à tous les utilisateurs");
    }
    
    @Data
    public static class BroadcastNotificationDTO {
        private String title;
        private String message;
    }
}

