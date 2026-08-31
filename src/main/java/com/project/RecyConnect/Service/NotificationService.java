package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.NotificationDTO;
import com.project.RecyConnect.Model.Notification;
import com.project.RecyConnect.Model.SupportedLanguage;
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
    private final NotificationMessages messages;

    public NotificationService(NotificationRepository repo, UserRepo userRepo,
                               FCMService fcmService, WebSocketService webSocketService,
                               UserSessionManager sessionManager,
                               NotificationMessages messages) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.fcmService = fcmService;
        this.webSocketService = webSocketService;
        this.sessionManager = sessionManager;
        this.messages = messages;
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
        
        // Envoyer en temps réel si receiverId est défini
        if (dto.getReceiverId() != null) {
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
     */
    private void sendNotification(NotificationDTO dto) {
        // S'assurer que createdAt est défini
        if (dto.getCreatedAt() == null) {
            dto.setCreatedAt(OffsetDateTime.now());
        }
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
     * Envoie une notification redigee dans la langue de son DESTINATAIRE.
     *
     * <p>Remplace {@code sendNegotiationNotification(..., String title, String
     * message)}, qui obligeait l'appelant a rediger le texte alors qu'il ne
     * connaissait du destinataire que son identifiant — d'ou des notifications
     * francaises pour tout le monde. Ici l'appelant declare ce qui s'est passe
     * (le {@code type}) et les donnees variables ({@code args}); la langue est
     * relue en base, puis appliquee une seule fois, ce qui garantit que le
     * texte pousse par FCM et celui garde en base sont identiques.
     *
     * <p>La langue prise en compte est toujours celle du destinataire, jamais
     * celle de l'auteur de l'action.
     *
     * @param args valeurs des emplacements {@code {0}}, {@code {1}}… du libelle
     */
    public void sendLocalizedNotification(Long receiverId, Long senderId,
                                          Long relatedId, String type,
                                          Object... args) {
        dispatch(languageOf(receiverId), receiverId, senderId, relatedId, type, args);
    }

    /**
     * Redige et envoie, la langue du destinataire etant deja connue.
     *
     * <p>Existe pour les appelants qui ont deja eu besoin de cette langue —
     * ceux qui composent le texte avec le nom d'un tiers. Sans cette variante,
     * chaque notification relisait deux fois le meme compte, et
     * {@code notifyOutbidUsers} le fait pour toute une file d'offres.
     */
    private void dispatch(SupportedLanguage language, Long receiverId, Long senderId,
                          Long relatedId, String type, Object... args) {
        NotificationMessages.Text text = messages.textFor(type, language, args);

        NotificationDTO notification = new NotificationDTO();
        notification.setReceiverId(receiverId);
        notification.setSenderId(senderId);
        notification.setTitle(text.title());
        notification.setMessage(text.body());
        notification.setCreatedAt(OffsetDateTime.now());
        notification.setType(type);
        notification.setRelatedId(relatedId);
        notification.setIsRead(false);

        sendNotification(notification);
    }

    /**
     * Envoie notification quand une offre est créée
     */
    public void sendOfferNotification(Long receiverId, Long senderId, 
                                     Long negotiationId, String productTitle) {
        SupportedLanguage language = languageOf(receiverId);
        dispatch(language, receiverId, senderId, negotiationId, "OFFER_RECEIVED",
                nameOf(senderId, language), productTitle);
    }
    
    /**
     * Envoie notification quand une offre est refusée
     */
    public void sendRefusalNotification(Long senderId, Long receiverId,
                                       Long negotiationId, String productTitle) {
        // Le sender de l'offre est celui qui recoit la notification: c'est donc
        // sa langue, et le nom de l'autre partie, qui composent le texte.
        SupportedLanguage language = languageOf(senderId);
        dispatch(language, senderId, receiverId, negotiationId, "OFFER_REFUSED",
                nameOf(receiverId, language), productTitle);
    }

    /**
     * La langue de notification d'un compte, francais a defaut.
     *
     * <p>Un destinataire introuvable — compte supprime entre l'evenement et
     * l'envoi — ne doit pas interrompre la chaine: la notification part en
     * francais et {@code sendNotification} constatera plus loin qu'il n'y a
     * personne a qui la remettre.
     */
    private SupportedLanguage languageOf(Long userId) {
        if (userId == null) {
            return SupportedLanguage.DEFAULT;
        }
        return userRepo.findById(userId)
                .map(SupportedLanguage::of)
                .orElse(SupportedLanguage.DEFAULT);
    }

    /** Le nom d'un compte, ou un terme generique traduit s'il a disparu. */
    private String nameOf(Long userId, SupportedLanguage language) {
        if (userId == null) {
            return messages.unknownSender(language);
        }
        return userRepo.findById(userId)
                .map(User::getUsername)
                .filter(name -> !name.isBlank())
                .orElseGet(() -> messages.unknownSender(language));
    }
    
    /**
     * Envoie une notification broadcast à TOUS les utilisateurs
     * Utilisé par l'admin pour les annonces, forfaits, etc.
     *
     * <p>Seule notification qui echappe a la traduction, et c'est structurel:
     * son texte est saisi librement par un administrateur, il n'existe donc
     * aucun libelle a traduire. L'envoi push passe d'ailleurs par le topic
     * {@code all_users} — un message unique pour tout le parc, qui ne peut par
     * construction pas varier selon le destinataire. Traduire un broadcast
     * supposerait de demander ses trois versions a l'administrateur.
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
    
    /**
     * Compte les notifications non lues d'un utilisateur
     */
    public long countUnreadNotifications(Long userId) {
        return repo.countUnreadByReceiverId(userId);
    }
}
