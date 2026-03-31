package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.NotificationDTO;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.UserRepo;
import com.project.RecyConnect.Service.FCMService;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fcm-test")
public class FCMTestController {
    
    private final FCMService fcmService;
    private final UserRepo userRepo;
    
    public FCMTestController(FCMService fcmService, UserRepo userRepo) {
        this.fcmService = fcmService;
        this.userRepo = userRepo;
    }
    
    /**
     * 1. Vérifier le statut de Firebase
     * GET /api/fcm-test/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        boolean isInitialized = !FirebaseApp.getApps().isEmpty();
        
        status.put("firebaseInitialized", isInitialized);
        status.put("timestamp", OffsetDateTime.now().toString());
        
        if (isInitialized) {
            status.put("appName", FirebaseApp.getInstance().getName());
            status.put("message", "Firebase est prêt à envoyer des notifications");
        } else {
            status.put("message", "Firebase n'est pas initialisé. Vérifiez service-account-key.json");
        }
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * 2. Lister tous les utilisateurs avec leur statut FCM
     * GET /api/fcm-test/users
     */
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getUsersWithFcmStatus() {
        List<Map<String, Object>> users = userRepo.findAll().stream()
            .map(user -> {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("id", user.getId());
                userInfo.put("username", user.getUsername());
                userInfo.put("phone", user.getPhone());
                userInfo.put("hasFcmToken", user.getFcmToken() != null && !user.getFcmToken().isEmpty());
                if (user.getFcmToken() != null && !user.getFcmToken().isEmpty()) {
                    userInfo.put("fcmTokenPreview", user.getFcmToken().substring(0, Math.min(30, user.getFcmToken().length())) + "...");
                }
                return userInfo;
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(users);
    }
    
    /**
     * 3. Envoyer une notification de test à un utilisateur
     * POST /api/fcm-test/send/{userId}
     */
    @PostMapping("/send/{userId}")
    public ResponseEntity<Map<String, Object>> sendTestNotification(
            @PathVariable Long userId,
            @RequestBody(required = false) TestNotificationDTO dto) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", OffsetDateTime.now().toString());
        result.put("userId", userId);
        
        // Vérifier Firebase
        if (FirebaseApp.getApps().isEmpty()) {
            result.put("success", false);
            result.put("error", "Firebase n'est pas initialisé");
            return ResponseEntity.badRequest().body(result);
        }
        
        // Vérifier l'utilisateur
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            result.put("success", false);
            result.put("error", "Utilisateur non trouvé");
            return ResponseEntity.badRequest().body(result);
        }
        
        result.put("username", user.getUsername());
        
        // Vérifier le token FCM
        if (user.getFcmToken() == null || user.getFcmToken().isEmpty()) {
            result.put("success", false);
            result.put("error", "Cet utilisateur n'a pas de token FCM enregistré");
            result.put("solution", "L'app mobile doit appeler POST /api/users/" + userId + "/fcm-token avec le token Firebase");
            return ResponseEntity.badRequest().body(result);
        }
        
        // Valeurs par défaut si dto est null
        String title = (dto != null && dto.getTitle() != null) ? dto.getTitle() : "🔔 Test Notification RecyConnect";
        String message = (dto != null && dto.getMessage() != null) ? dto.getMessage() : "Ceci est une notification de test envoyée à " + OffsetDateTime.now();
        String type = (dto != null && dto.getType() != null) ? dto.getType() : "TEST";
        
        try {
            Message fcmMessage = Message.builder()
                .setToken(user.getFcmToken())
                .setNotification(Notification.builder()
                    .setTitle(title)
                    .setBody(message)
                    .build())
                .setAndroidConfig(AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(AndroidNotification.builder()
                        .setChannelId("recyconnect_high_importance")
                        .setPriority(AndroidNotification.Priority.MAX)
                        .setSound("default")
                        .setDefaultVibrateTimings(true)
                        .setDefaultLightSettings(true)
                        .setVisibility(AndroidNotification.Visibility.PUBLIC)
                        .build())
                    .build())
                .putData("type", type)
                .putData("timestamp", String.valueOf(System.currentTimeMillis()))
                .putData("testId", "TEST_" + System.currentTimeMillis())
                .build();
            
            String messageId = FirebaseMessaging.getInstance().send(fcmMessage);
            
            result.put("success", true);
            result.put("messageId", messageId);
            result.put("notificationTitle", title);
            result.put("notificationMessage", message);
            result.put("fcmTokenUsed", user.getFcmToken().substring(0, 30) + "...");
            
            return ResponseEntity.ok(result);
            
        } catch (FirebaseMessagingException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("errorCode", e.getMessagingErrorCode() != null ? e.getMessagingErrorCode().toString() : "UNKNOWN");
            
            // Si token invalide, suggérer de le mettre à jour
            if (e.getMessagingErrorCode() != null) {
                String errorCode = e.getMessagingErrorCode().toString();
                if (errorCode.contains("INVALID") || errorCode.contains("UNREGISTERED")) {
                    result.put("solution", "Le token FCM est invalide ou expiré. L'app mobile doit enregistrer un nouveau token.");
                }
            }
            
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
    
    /**
     * 4. Envoyer une notification directement avec un token FCM (sans utilisateur)
     * POST /api/fcm-test/send-direct
     */
    @PostMapping("/send-direct")
    public ResponseEntity<Map<String, Object>> sendDirectNotification(@RequestBody DirectNotificationDTO dto) {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", OffsetDateTime.now().toString());
        
        if (dto.getFcmToken() == null || dto.getFcmToken().isEmpty()) {
            result.put("success", false);
            result.put("error", "Le token FCM est requis");
            return ResponseEntity.badRequest().body(result);
        }
        
        if (FirebaseApp.getApps().isEmpty()) {
            result.put("success", false);
            result.put("error", "Firebase n'est pas initialisé");
            return ResponseEntity.badRequest().body(result);
        }
        
        String title = dto.getTitle() != null ? dto.getTitle() : "🔔 Test Direct FCM";
        String message = dto.getMessage() != null ? dto.getMessage() : "Notification envoyée directement via token";
        
        try {
            Message fcmMessage = Message.builder()
                .setToken(dto.getFcmToken())
                .setNotification(Notification.builder()
                    .setTitle(title)
                    .setBody(message)
                    .build())
                .setAndroidConfig(AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(AndroidNotification.builder()
                        .setChannelId("recyconnect_high_importance")
                        .setPriority(AndroidNotification.Priority.MAX)
                        .setSound("default")
                        .setDefaultVibrateTimings(true)
                        .setDefaultLightSettings(true)
                        .setVisibility(AndroidNotification.Visibility.PUBLIC)
                        .build())
                    .build())
                .putData("type", "DIRECT_TEST")
                .putData("timestamp", String.valueOf(System.currentTimeMillis()))
                .build();
            
            String messageId = FirebaseMessaging.getInstance().send(fcmMessage);
            
            result.put("success", true);
            result.put("messageId", messageId);
            result.put("title", title);
            result.put("message", message);
            
            return ResponseEntity.ok(result);
            
        } catch (FirebaseMessagingException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("errorCode", e.getMessagingErrorCode() != null ? e.getMessagingErrorCode().toString() : "UNKNOWN");
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    /**
     * 5. Valider un token FCM (dry-run, n'envoie pas vraiment)
     * POST /api/fcm-test/validate-token
     */
    @PostMapping("/validate-token")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestBody ValidateTokenDTO dto) {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", OffsetDateTime.now().toString());
        result.put("tokenPreview", dto.getFcmToken() != null ? 
            dto.getFcmToken().substring(0, Math.min(30, dto.getFcmToken().length())) + "..." : "null");
        
        if (dto.getFcmToken() == null || dto.getFcmToken().isEmpty()) {
            result.put("valid", false);
            result.put("error", "Token non fourni");
            return ResponseEntity.badRequest().body(result);
        }
        
        if (FirebaseApp.getApps().isEmpty()) {
            result.put("valid", false);
            result.put("error", "Firebase n'est pas initialisé");
            return ResponseEntity.badRequest().body(result);
        }
        
        try {
            // Utiliser dryRun pour valider sans envoyer
            Message message = Message.builder()
                .setToken(dto.getFcmToken())
                .setNotification(Notification.builder()
                    .setTitle("Validation")
                    .setBody("Test")
                    .build())
                .build();
            
            // sendAsync avec dryRun=true
            String response = FirebaseMessaging.getInstance().send(message, true);
            
            result.put("valid", true);
            result.put("message", "Token valide et actif");
            result.put("dryRunResponse", response);
            
            return ResponseEntity.ok(result);
            
        } catch (FirebaseMessagingException e) {
            result.put("valid", false);
            result.put("error", e.getMessage());
            result.put("errorCode", e.getMessagingErrorCode() != null ? e.getMessagingErrorCode().toString() : "UNKNOWN");
            
            if (e.getMessagingErrorCode() != null) {
                String code = e.getMessagingErrorCode().toString();
                if (code.contains("INVALID")) {
                    result.put("reason", "Token mal formé ou invalide");
                } else if (code.contains("UNREGISTERED")) {
                    result.put("reason", "Token expiré ou l'app a été désinstallée");
                } else if (code.contains("SENDER_ID_MISMATCH")) {
                    result.put("reason", "Le token appartient à un autre projet Firebase");
                }
            }
            
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    /**
     * 6. Enregistrer/Mettre à jour le token FCM d'un utilisateur
     * POST /api/fcm-test/register-token/{userId}
     */
    @PostMapping("/register-token/{userId}")
    public ResponseEntity<Map<String, Object>> registerToken(
            @PathVariable Long userId,
            @RequestBody RegisterTokenDTO dto) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", OffsetDateTime.now().toString());
        result.put("userId", userId);
        
        if (dto.getFcmToken() == null || dto.getFcmToken().isEmpty()) {
            result.put("success", false);
            result.put("error", "Token FCM requis");
            return ResponseEntity.badRequest().body(result);
        }
        
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            result.put("success", false);
            result.put("error", "Utilisateur non trouvé");
            return ResponseEntity.badRequest().body(result);
        }
        
        String oldToken = user.getFcmToken();
        user.setFcmToken(dto.getFcmToken());
        userRepo.save(user);
        
        result.put("success", true);
        result.put("username", user.getUsername());
        result.put("previousToken", oldToken != null ? oldToken.substring(0, Math.min(20, oldToken.length())) + "..." : "null");
        result.put("newToken", dto.getFcmToken().substring(0, Math.min(20, dto.getFcmToken().length())) + "...");
        result.put("message", "Token FCM mis à jour avec succès");
        
        return ResponseEntity.ok(result);
    }
    
    // DTO classes
    @Data
    public static class TestNotificationDTO {
        private String title;
        private String message;
        private String type;
    }
    
    @Data
    public static class DirectNotificationDTO {
        private String fcmToken;
        private String title;
        private String message;
    }
    
    @Data
    public static class ValidateTokenDTO {
        private String fcmToken;
    }
    
    @Data
    public static class RegisterTokenDTO {
        private String fcmToken;
    }
}
