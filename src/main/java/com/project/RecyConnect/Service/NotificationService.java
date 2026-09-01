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
        // Si createdAt n'est pas défini ou si c'est une nouvelle notification, utiliser la date actuelle
        if (dto.getCreatedAt() == null || dto.getId() == null) {
            n.setCreatedAt(OffsetDateTime.now());
        } else {
            n.setCreatedAt(dto.getCreatedAt());
        }
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
        // S'assurer que createdAt est défini pour les nouvelles notifications
        if (dto.getCreatedAt() == null || dto.getId() == null) {
            dto.setCreatedAt(OffsetDateTime.now());
        }
        
        // Sauvegarder en DB
        Notification saved = repo.save(fromDTO(dto));
        NotificationDTO savedDTO = toDTO(saved);
        
        // Envoyer en temps réel si receiverId est défini — et si le
        // destinataire accepte encore ce genre de notification.
        if (dto.getReceiverId() != null && isDeliveryAllowed(dto.getReceiverId(), dto.getType())) {
            boolean isOnline = sessionManager.isUserConnected(dto.getReceiverId());
            
            if (isOnline) {
                // User en ligne → WebSocket (instantané)
                webSocketService.sendToUser(dto.getReceiverId(), savedDTO);
            } else {
                // User hors ligne → FCM (notification push)
                fcmService.sendPushNotification(dto.getReceiverId(), savedDTO);
            }
        }
        
        return savedDTO;
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
     *
     * <p>La notification est enregistrée quoi qu'il arrive: couper un
     * interrupteur doit faire taire le téléphone, pas effacer l'information.
     * Elle reste donc lisible dans la boîte de réception de l'application;
     * seule la remise immédiate — bandeau système ou WebSocket — est suspendue.
     */
    private void sendNotification(NotificationDTO dto) {
        // S'assurer que createdAt est défini
        if (dto.getCreatedAt() == null) {
            dto.setCreatedAt(OffsetDateTime.now());
        }
        // 1. Sauvegarder en DB
        Notification saved = repo.save(fromDTO(dto));
        NotificationDTO savedDTO = toDTO(saved);

        if (!isDeliveryAllowed(dto.getReceiverId(), dto.getType())) {
            return;
        }

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
     * Le destinataire accepte-t-il encore ce genre de notification ?
     *
     * <p>Trois familles seulement, celles que proposent les réglages: les
     * offres, les messages du service, les annonces de l'équipe. Un type
     * inconnu est traité comme un message du service — un nouveau type ne doit
     * pas se retrouver muet par défaut.
     *
     * <p>Une préférence jamais exprimée vaut consentement: c'était le
     * comportement avant l'ajout des colonnes.
     */
    private boolean isDeliveryAllowed(Long receiverId, String type) {
        if (receiverId == null) {
            return false;
        }
        User receiver = userRepo.findById(receiverId).orElse(null);
        if (receiver == null) {
            return false;
        }

        String kind = type != null ? type.toUpperCase() : "";
        if (kind.startsWith("OFFER") || kind.startsWith("QUEUE") || kind.startsWith("OUTBID")) {
            return receiver.getNotifyOffers() == null || receiver.getNotifyOffers();
        }
        if (kind.equals("BROADCAST") || kind.startsWith("PROMO")) {
            return receiver.getNotifyPromotions() == null || receiver.getNotifyPromotions();
        }
        return receiver.getNotifySystem() == null || receiver.getNotifySystem();
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

    public void sendNegotiationNotification(Long receiverId, Long senderId,
                                            Long negotiationId, String type,
                                            String title, String message) {
        NotificationDTO notification = new NotificationDTO();
        notification.setReceiverId(receiverId);
        notification.setSenderId(senderId);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setCreatedAt(OffsetDateTime.now());
        notification.setType(type);
        notification.setRelatedId(negotiationId);
        notification.setIsRead(false);

        sendNotification(notification);
    }
    
    /**
     * Envoie une notification broadcast à TOUS les utilisateurs
     * Utilisé par l'admin pour les annonces, forfaits, etc.
     */
    public void sendBroadcastToAllUsers(String title, String message) {
        // Compte par compte, et non par topic FCM: un topic ne sait pas
        // distinguer ceux qui ont coupé les annonces de l'équipe dans leurs
        // réglages. Chacun reçoit sa copie en base — la boîte de réception
        // reste complète —, et seule la remise immédiate suit la préférence.
        //
        // Au passage, les comptes connectés reçoivent enfin la diffusion par
        // WebSocket: le topic ne touchait que les appareils abonnés à FCM.
        for (User user : userRepo.findAll()) {
            NotificationDTO notification = new NotificationDTO();
            notification.setReceiverId(user.getId());
            notification.setSenderId(null); // Pas de sender pour les notifications admin
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setType("BROADCAST");
            notification.setCreatedAt(OffsetDateTime.now());
            notification.setIsRead(false);

            sendNotification(notification);
        }
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
    
    /**
     * Compte les notifications non lues d'un utilisateur
     */
    public long countUnreadNotifications(Long userId) {
        return repo.countUnreadByReceiverId(userId);
    }
}
