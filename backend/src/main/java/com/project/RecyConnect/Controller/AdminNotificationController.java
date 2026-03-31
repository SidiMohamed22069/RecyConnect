package com.project.RecyConnect.Controller;

import com.project.RecyConnect.Service.FCMService;
import com.project.RecyConnect.Service.NotificationService;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {
    
    private final NotificationService notificationService;
    private final FCMService fcmService;
    
    public AdminNotificationController(NotificationService notificationService, FCMService fcmService) {
        this.notificationService = notificationService;
        this.fcmService = fcmService;
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
    
    /**
     * Endpoint pour tester FCM - envoie une notification de test à un utilisateur
     */
    @GetMapping("/test-fcm/{userId}")
    public ResponseEntity<String> testFcm(@PathVariable Long userId) {
        String result = fcmService.testFcmConnection(userId);
        return ResponseEntity.ok(result);
    }
    
    /**
     * Endpoint pour vérifier le statut de la config FCM
     */
    @GetMapping("/fcm-status")
    public ResponseEntity<String> getFcmStatus() {
        String status = fcmService.getFcmStatus();
        return ResponseEntity.ok(status);
    }
    
    @Data
    public static class BroadcastNotificationDTO {
        private String title;
        private String message;
    }
}

