package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.NotificationDTO;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.UserRepo;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;

@Service
public class FCMService {
    
    @Value("${fcm.service-account-key:}")
    private String serviceAccountKeyPath;
    
    @Value("${fcm.project-id:}")
    private String projectId;
    
    private final UserRepo userRepo;
    
    public FCMService(UserRepo userRepo) {
        this.userRepo = userRepo;
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
     * Envoie une notification push à un utilisateur spécifique
     */
    public void sendPushNotification(Long userId, NotificationDTO notificationDTO) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null || user.getFcmToken() == null || user.getFcmToken().isEmpty()) {
            return; // Pas de token FCM, on ne peut pas envoyer
        }
        
        try {
            Message message = Message.builder()
                .setToken(user.getFcmToken())
                .setNotification(Notification.builder()
                    .setTitle(notificationDTO.getTitle())
                    .setBody(notificationDTO.getMessage())
                    .build())
                .putData("type", notificationDTO.getType() != null ? notificationDTO.getType() : "")
                .putData("relatedId", notificationDTO.getRelatedId() != null ? notificationDTO.getRelatedId().toString() : "")
                .putData("notificationId", notificationDTO.getId() != null ? notificationDTO.getId().toString() : "")
                .build();
            
            FirebaseMessaging.getInstance().send(message);
        } catch (FirebaseMessagingException e) {
            System.err.println("Erreur lors de l'envoi de la notification FCM: " + e.getMessage());
            // Si le token est invalide, on peut le supprimer
            if (e.getMessagingErrorCode() != null && 
                (e.getMessagingErrorCode().equals("INVALID_ARGUMENT") || 
                 e.getMessagingErrorCode().equals("UNREGISTERED"))) {
                user.setFcmToken(null);
                userRepo.save(user);
            }
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
                .putData("type", "BROADCAST")
                .build();
            
            FirebaseMessaging.getInstance().send(fcmMessage);
        } catch (FirebaseMessagingException e) {
            System.err.println("Erreur lors de l'envoi broadcast FCM: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Erreur inattendue lors de l'envoi broadcast: " + e.getMessage());
        }
    }
}

