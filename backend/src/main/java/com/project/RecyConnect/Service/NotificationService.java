package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.NotificationDTO;
import com.project.RecyConnect.Model.Notification;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.NotificationRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class NotificationService {
    private final NotificationRepository repo;
    private final UserRepo userRepo;
    private final FCMService fcmService;
    private final WebSocketService webSocketService;
    private final UserSessionManager sessionManager;

    public NotificationService(NotificationRepository repo, UserRepo userRepo,
                               FCMService fcmService, WebSocketService webSocketService,
                               UserSessionManager sessionManager) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.fcmService = fcmService;
        this.webSocketService = webSocketService;
        this.sessionManager = sessionManager;
    }

    private NotificationDTO toDTO(Notification n) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(n.getId());
        dto.setCreatedAt(n.getCreatedAt());
        dto.setSenderId(n.getSender() != null ? n.getSender().getId() : null);
        dto.setReceiverId(n.getReceiver() != null ? n.getReceiver().getId() : null);
        dto.setTitle(n.getTitle());
        dto.setMessage(n.getMessage());
        dto.setType(n.getType());
        dto.setRelatedId(n.getRelatedId());
        dto.setIsRead(n.getIsRead() != null ? n.getIsRead() : false);
        return dto;
    }

    private Notification fromDTO(NotificationDTO dto) {
        Notification n = new Notification();
        n.setId(dto.getId());
        n.setCreatedAt(dto.getCreatedAt());
        n.setTitle(dto.getTitle());
        n.setMessage(dto.getMessage());
        n.setType(dto.getType());
        n.setRelatedId(dto.getRelatedId());
        n.setIsRead(dto.getIsRead() != null ? dto.getIsRead() : false);
        if (dto.getSenderId() != null)
            userRepo.findById(dto.getSenderId()).ifPresent(n::setSender);
        if (dto.getReceiverId() != null)
            userRepo.findById(dto.getReceiverId()).ifPresent(n::setReceiver);
        return n;
    }

    public List<NotificationDTO> findAll() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public Optional<NotificationDTO> findById(Long id) {
        return repo.findById(id).map(this::toDTO);
    }

    public List<NotificationDTO> findByReceiverId(Long receiverId) {
        return repo.findByReceiverId(receiverId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    public NotificationDTO save(NotificationDTO dto) {
        return toDTO(repo.save(fromDTO(dto)));
    }

    public NotificationDTO update(Long id, NotificationDTO dto) {
        return repo.findById(id).map(existing -> {
            existing.setTitle(dto.getTitle());
            existing.setMessage(dto.getMessage());
            return toDTO(repo.save(existing));
        }).orElseThrow(() -> new RuntimeException("Notification not found"));
    }

    public NotificationDTO patch(Long id, NotificationDTO dto) {
        return repo.findById(id).map(existing -> {
            if (dto.getTitle() != null) existing.setTitle(dto.getTitle());
            if (dto.getMessage() != null) existing.setMessage(dto.getMessage());
            if (dto.getSenderId() != null)
                userRepo.findById(dto.getSenderId()).ifPresent(existing::setSender);
            if (dto.getReceiverId() != null)
                userRepo.findById(dto.getReceiverId()).ifPresent(existing::setReceiver);
            return toDTO(repo.save(existing));
        }).orElseThrow(() -> new RuntimeException("Notification not found"));
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }
    
    /**
     * Envoie une notification à un utilisateur (WebSocket si en ligne, FCM sinon)
     */
    private void sendNotification(NotificationDTO dto) {
        // 1. Sauvegarder en DB
        Notification saved = repo.save(fromDTO(dto));
        NotificationDTO savedDTO = toDTO(saved);
        
        // 2. Vérifier si user est connecté via WebSocket
        boolean isOnline = sessionManager.isUserConnected(dto.getReceiverId());
        
        if (isOnline) {
            // User en ligne → WebSocket (instantané, gratuit)
            webSocketService.sendToUser(dto.getReceiverId(), savedDTO);
        } else {
            // User hors ligne → FCM (notification push)
            fcmService.sendPushNotification(dto.getReceiverId(), savedDTO);
        }
    }
    
    /**
     * Envoie notification quand une offre est créée
     */
    public void sendOfferNotification(Long receiverId, Long senderId, 
                                     Long negotiationId, String productTitle) {
        User sender = userRepo.findById(senderId).orElse(null);
        String senderName = sender != null ? sender.getUsername() : "Un utilisateur";
        
        NotificationDTO notification = new NotificationDTO();
        notification.setReceiverId(receiverId);
        notification.setSenderId(senderId);
        notification.setTitle("Nouvelle offre reçue");
        notification.setMessage(senderName + " vous a fait une offre pour: " + productTitle);
        notification.setCreatedAt(OffsetDateTime.now());
        notification.setType("OFFER_RECEIVED");
        notification.setRelatedId(negotiationId);
        notification.setIsRead(false);
        
        sendNotification(notification);
    }
    
    /**
     * Envoie notification quand une offre est refusée
     */
    public void sendRefusalNotification(Long senderId, Long receiverId,
                                       Long negotiationId, String productTitle) {
        User receiver = userRepo.findById(receiverId).orElse(null);
        String receiverName = receiver != null ? receiver.getUsername() : "Un utilisateur";
        
        NotificationDTO notification = new NotificationDTO();
        notification.setReceiverId(senderId);  // Le sender de l'offre reçoit la notification
        notification.setSenderId(receiverId);
        notification.setTitle("Offre refusée");
        notification.setMessage(receiverName + " a refusé votre offre pour: " + productTitle);
        notification.setCreatedAt(OffsetDateTime.now());
        notification.setType("OFFER_REFUSED");
        notification.setRelatedId(negotiationId);
        notification.setIsRead(false);
        
        sendNotification(notification);
    }
    
    /**
     * Envoie une notification broadcast à TOUS les utilisateurs
     * Utilisé par l'admin pour les annonces, forfaits, etc.
     */
    public void sendBroadcastToAllUsers(String title, String message) {
        // 1. Créer une notification pour chaque user en DB
        List<User> allUsers = userRepo.findAll();
        
        for (User user : allUsers) {
            Notification notification = new Notification();
            notification.setReceiver(user);
            notification.setSender(null); // Pas de sender pour les notifications admin
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setType("BROADCAST");
            notification.setCreatedAt(OffsetDateTime.now());
            notification.setIsRead(false);
            
            repo.save(notification);
        }
        
        // 2. Envoyer via FCM Topic (tous les users abonnés)
        fcmService.sendBroadcastNotification(title, message);
    }
    
    /**
     * Marque une notification comme lue
     */
    public void markAsRead(Long notificationId) {
        repo.findById(notificationId).ifPresent(notification -> {
            notification.setIsRead(true);
            repo.save(notification);
        });
    }
    
    /**
     * Récupère les notifications non lues d'un utilisateur
     */
    public List<NotificationDTO> getUnreadNotifications(Long userId) {
        return repo.findByReceiverId(userId).stream()
                .filter(n -> n.getIsRead() == null || !n.getIsRead())
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}
