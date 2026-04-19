package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.NotificationDTO;
import com.project.RecyConnect.Model.PendingLogin;
import com.project.RecyConnect.Model.PendingLogin.PendingLoginStatus;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Model.UserDevice;
import com.project.RecyConnect.Repository.PendingLoginRepository;
import com.project.RecyConnect.Repository.UserDeviceRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PendingLoginService {

    private final PendingLoginRepository pendingLoginRepository;
    private final UserDeviceRepository deviceRepository;
    private final UserRepo userRepo;
    private final FCMService fcmService;
    private final WebSocketService webSocketService;
    private final UserSessionManager sessionManager;

    public PendingLoginService(
            PendingLoginRepository pendingLoginRepository,
            UserDeviceRepository deviceRepository,
            UserRepo userRepo,
            FCMService fcmService,
            WebSocketService webSocketService,
            UserSessionManager sessionManager) {
        this.pendingLoginRepository = pendingLoginRepository;
        this.deviceRepository = deviceRepository;
        this.userRepo = userRepo;
        this.fcmService = fcmService;
        this.webSocketService = webSocketService;
        this.sessionManager = sessionManager;
    }

    /**
     * Vérifie si l'utilisateur a déjà un appareil connecté
     */
    public boolean hasExistingDevice(Long userId) {
        return !deviceRepository.findByUserId(userId).isEmpty();
    }

    /**
     * Crée une demande de connexion en attente et notifie l'ancien appareil
     */
    @Transactional
    public PendingLogin createPendingLogin(Long userId, String fcmToken, String deviceName, String deviceType) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Générer un ID unique pour cette demande
        String requestId = UUID.randomUUID().toString();

        PendingLogin pending = PendingLogin.builder()
                .user(user)
                .requestId(requestId)
                .newDeviceFcmToken(fcmToken)
                .newDeviceName(deviceName != null ? deviceName : "Appareil inconnu")
                .newDeviceType(deviceType != null ? deviceType : "UNKNOWN")
                .status(PendingLoginStatus.PENDING)
                .build();

        PendingLogin saved = pendingLoginRepository.save(pending);

        // Notifier l'ancien appareil
        notifyExistingDevice(user, saved);

        return saved;
    }

    /**
     * Envoie une notification à l'ancien appareil pour demander confirmation
     */
    private void notifyExistingDevice(User user, PendingLogin pending) {
        NotificationDTO notification = new NotificationDTO();
        notification.setReceiverId(user.getId());
        notification.setTitle("🔐 Nouvelle tentative de connexion");
        notification.setMessage("Un appareil \"" + pending.getNewDeviceName() + "\" essaie de se connecter à votre compte. Est-ce vous ?");
        notification.setType("LOGIN_REQUEST");
        notification.setRelatedId(pending.getId());
        notification.setCreatedAt(OffsetDateTime.now());

        // Envoyer via WebSocket si en ligne
        if (sessionManager.isUserConnected(user.getId())) {
            webSocketService.sendToUser(user.getId(), notification);
        }
        
        // Envoyer aussi via FCM (push notification)
        fcmService.sendPushNotification(user.getId(), notification);
    }

    /**
     * L'utilisateur approuve la connexion depuis l'ancien appareil
     */
    @Transactional
    public PendingLogin approveLogin(String requestId) {
        PendingLogin pending = pendingLoginRepository.findByRequestId(requestId)
                .orElseThrow(() -> new RuntimeException("Demande de connexion non trouvée"));

        if (pending.isExpired()) {
            pending.setStatus(PendingLoginStatus.EXPIRED);
            pendingLoginRepository.save(pending);
            throw new RuntimeException("Demande expirée");
        }

        if (pending.getStatus() != PendingLoginStatus.PENDING) {
            throw new RuntimeException("Cette demande a déjà été traitée");
        }

        // 1. Supprimer l'ancien appareil
        deviceRepository.deleteByUserId(pending.getUser().getId());

        // 2. Enregistrer le nouvel appareil
        UserDevice newDevice = UserDevice.builder()
                .user(pending.getUser())
                .fcmToken(pending.getNewDeviceFcmToken())
                .deviceName(pending.getNewDeviceName())
                .deviceType(pending.getNewDeviceType())
                .lastConnectedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .build();
        deviceRepository.save(newDevice);

        // 3. Mettre à jour le statut
        pending.setStatus(PendingLoginStatus.APPROVED);
        pending.setRespondedAt(OffsetDateTime.now());
        
        return pendingLoginRepository.save(pending);
    }

    /**
     * L'utilisateur refuse la connexion depuis l'ancien appareil
     */
    @Transactional
    public PendingLogin rejectLogin(String requestId) {
        PendingLogin pending = pendingLoginRepository.findByRequestId(requestId)
                .orElseThrow(() -> new RuntimeException("Demande de connexion non trouvée"));

        if (pending.getStatus() != PendingLoginStatus.PENDING) {
            throw new RuntimeException("Cette demande a déjà été traitée");
        }

        pending.setStatus(PendingLoginStatus.REJECTED);
        pending.setRespondedAt(OffsetDateTime.now());
        
        return pendingLoginRepository.save(pending);
    }

    /**
     * Vérifie le statut d'une demande de connexion (polling par le nouvel appareil)
     */
    public Optional<PendingLogin> checkStatus(String requestId) {
        return pendingLoginRepository.findByRequestId(requestId);
    }

    /**
     * Nettoie les demandes expirées (exécuté toutes les 5 minutes)
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    public void cleanupExpired() {
        pendingLoginRepository.deleteExpired(OffsetDateTime.now());
    }
}
