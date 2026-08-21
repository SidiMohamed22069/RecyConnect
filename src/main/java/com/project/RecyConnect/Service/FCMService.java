package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.NotificationDTO;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Model.UserSession;
import com.project.RecyConnect.Repository.UserRepo;
import com.project.RecyConnect.Repository.UserSessionRepository;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;

@Service
public class FCMService {
    
    @Value("${fcm.service-account-key:}")
    private String serviceAccountKeyPath;
    
    @Value("${fcm.project-id:}")
    private String projectId;
    
    private final UserRepo userRepo;
    private final UserSessionRepository userSessionRepository;
    
    public FCMService(UserRepo userRepo, UserSessionRepository userSessionRepository) {
        this.userRepo = userRepo;
        this.userSessionRepository = userSessionRepository;
    }
    
    @PostConstruct
    public void initialize() {
        // Note: Vous devez configurer Firebase avec votre fichier service-account.json
        // Pour l'instant, on laisse vide - vous devrez ajouter la configuration
        try {
            if (serviceAccountKeyPath != null && !serviceAccountKeyPath.isEmpty()) {
                ClassPathResource resource = new ClassPathResource(serviceAccountKeyPath);
                GoogleCredentials credentials = GoogleCredentials.fromStream(resource.getInputStream());
                FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(projectId)
                    .build();
                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseApp.initializeApp(options);
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de l'initialisation de Firebase: " + e.getMessage());
            // L'application peut continuer sans FCM si non configuré
        }
    }
    
    /**
     * Envoie une notification push au seul appareil actif d'un utilisateur
     */
    public void sendPushNotification(Long userId, NotificationDTO notificationDTO) {
        UserSession session = userSessionRepository.findById(userId).orElse(null);
        if (session == null || session.getFcmToken() == null || session.getFcmToken().isEmpty()) {
            return;
        }

        sendToToken(session.getFcmToken(), notificationDTO);
    }
    
    /**
     * Envoie une notification à un token FCM spécifique
     * Envoie uniquement des DATA (pas de notification) pour éviter les doublons
     * C'est l'app Flutter qui crée la notification locale
     */
    private void sendToToken(String fcmToken, NotificationDTO notificationDTO) {
        try {
            Message message = Message.builder()
                .setToken(fcmToken)
                // PAS de .setNotification() pour éviter le doublon avec Flutter
                .setAndroidConfig(AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .build())
                .putData("title", notificationDTO.getTitle() != null ? notificationDTO.getTitle() : "")
                .putData("body", notificationDTO.getMessage() != null ? notificationDTO.getMessage() : "")
                .putData("type", notificationDTO.getType() != null ? notificationDTO.getType() : "")
                .putData("relatedId", notificationDTO.getRelatedId() != null ? notificationDTO.getRelatedId().toString() : "")
                .putData("notificationId", notificationDTO.getId() != null ? notificationDTO.getId().toString() : "")
                .build();
            
            FirebaseMessaging.getInstance().send(message);
        } catch (FirebaseMessagingException e) {
            System.err.println("Erreur lors de l'envoi de la notification FCM: " + e.getMessage());
            // Token invalide: la prochaine connexion régénérera la session.
        } catch (Exception e) {
            System.err.println("Erreur inattendue lors de l'envoi FCM: " + e.getMessage());
        }
    }
    
    /**
     * Envoie une notification broadcast à tous les utilisateurs via FCM Topic
     */
    public void sendBroadcastNotification(String title, String message) {
        try {
            Message fcmMessage = Message.builder()
                .setTopic("all_users")
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
                .putData("type", "BROADCAST")
                .build();
            
            FirebaseMessaging.getInstance().send(fcmMessage);
        } catch (FirebaseMessagingException e) {
            System.err.println("Erreur lors de l'envoi broadcast FCM: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Erreur inattendue lors de l'envoi broadcast: " + e.getMessage());
        }
    }

    /**
     * Envoie un push de déconnexion forcée à un appareil remplacé.
     * Mécanisme UX optionnel, la sécurité est assurée côté JWT/session.
     */
    public void sendForceLogoutToToken(String fcmToken, String reason) {
        if (fcmToken == null || fcmToken.isBlank()) {
            return;
        }

        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .putHeader("apns-priority", "10")
                            .setAps(Aps.builder().setContentAvailable(true).build())
                            .build())
                    .putData("type", "force_logout")
                    .putData("reason", reason == null ? "session_replaced" : reason)
                    .build();

            FirebaseMessaging.getInstance().send(message);
        } catch (Exception e) {
            // Best effort: ne pas casser la connexion utilisateur si le push échoue.
        }
    }
    
    /**
     * Teste la connexion FCM et envoie une notification de test à un utilisateur
     * @return Message de résultat du test
     */
    public String testFcmConnection(Long userId) {
        // Vérifier si Firebase est initialisé
        if (FirebaseApp.getApps().isEmpty()) {
            return "ERREUR: Firebase n'est pas initialisé. Vérifiez le fichier service-account-key.json";
        }
        
        UserSession session = userSessionRepository.findById(userId).orElse(null);
        if (session == null) {
            User user = userRepo.findById(userId).orElse(null);
            if (user == null) {
                return "ERREUR: Utilisateur avec ID " + userId + " non trouvé";
            }
            if (user.getFcmToken() == null || user.getFcmToken().isEmpty()) {
                return "ERREUR: L'utilisateur " + user.getUsername() + " n'a pas de session active avec token FCM.";
            }
            try {
                Message message = Message.builder()
                    .setToken(user.getFcmToken())
                    .setNotification(Notification.builder()
                        .setTitle("🧪 Test FCM RecyConnect")
                        .setBody("Si vous voyez cette notification, FCM fonctionne correctement!")
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
                    .putData("type", "TEST")
                    .putData("timestamp", String.valueOf(System.currentTimeMillis()))
                    .build();
            
                String messageId = FirebaseMessaging.getInstance().send(message);
                return "SUCCÈS: Notification envoyée! Message ID: " + messageId + 
                       " | Utilisateur: " + user.getUsername() + 
                       " | Token: " + user.getFcmToken().substring(0, Math.min(20, user.getFcmToken().length())) + "...";
            } catch (FirebaseMessagingException e) {
                return "ERREUR FCM: " + e.getMessage() + " | Code: " + e.getMessagingErrorCode();
            } catch (Exception e) {
                return "ERREUR: " + e.getMessage();
            }
        }

        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return "ERREUR: Utilisateur avec ID " + userId + " non trouvé";
        }

        if (session.getFcmToken() == null || session.getFcmToken().isEmpty()) {
            return "ERREUR: L'utilisateur " + user.getUsername() + " n'a pas de token FCM dans sa session active.";
        }

        try {
            Message message = Message.builder()
                .setToken(session.getFcmToken())
                .setNotification(Notification.builder()
                    .setTitle("🧪 Test FCM RecyConnect")
                    .setBody("Si vous voyez cette notification, FCM fonctionne correctement!")
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
                .putData("type", "TEST")
                .putData("timestamp", String.valueOf(System.currentTimeMillis()))
                .build();
        
            String messageId = FirebaseMessaging.getInstance().send(message);
            return "SUCCÈS: Notification envoyée! Message ID: " + messageId + 
                     " | Utilisateur: " + user.getUsername() + 
                   " | Token: " + session.getFcmToken().substring(0, Math.min(20, session.getFcmToken().length())) + "...";
        } catch (FirebaseMessagingException e) {
            return "ERREUR FCM: " + e.getMessage() + " | Code: " + e.getMessagingErrorCode();
        } catch (Exception e) {
            return "ERREUR: " + e.getMessage();
        }
    }
    
    /**
     * Vérifie le statut de la configuration FCM
     */
    public String getFcmStatus() {
        StringBuilder status = new StringBuilder();
        status.append("=== Statut FCM ===\n");
        status.append("Firebase initialisé: ").append(!FirebaseApp.getApps().isEmpty()).append("\n");
        status.append("Service Account Key: ").append(serviceAccountKeyPath).append("\n");
        status.append("Project ID: ").append(projectId).append("\n");
        
        if (!FirebaseApp.getApps().isEmpty()) {
            status.append("Firebase App Name: ").append(FirebaseApp.getInstance().getName()).append("\n");
        }
        
        return status.toString();
    }
}

