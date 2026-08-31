package com.project.RecyConnect.Service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import com.project.RecyConnect.DTO.NotificationDTO;
import com.project.RecyConnect.Model.SupportedLanguage;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Model.UserSession;
import com.project.RecyConnect.Repository.UserRepo;
import com.project.RecyConnect.Repository.UserSessionRepository;

import jakarta.annotation.PostConstruct;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Service
public class FCMService {


// ============================================================
// CONSTANTES
// ============================================================

private static final String ANDROID_CHANNEL_ID =
        "recyconnect_high_importance";

private static final String APNS_PRIORITY_ALERT = "10";
private static final String APNS_PRIORITY_BACKGROUND = "5";

private static final String APNS_PUSH_TYPE_ALERT = "alert";
private static final String APNS_PUSH_TYPE_BACKGROUND = "background";

private static final String TOPIC_ALL_USERS = "all_users";

// ============================================================
// CONFIGURATION
// ============================================================

/**
 * Chemin vers le fichier Firebase Service Account.
 *
 * Exemples :
 *
 * file:/app/secrets/firebase-service-account.json
 * classpath:firebase-service-account.json
 * firebase-service-account.json
 */
@Value("${fcm.service-account-key:}")
private String serviceAccountKeyPath;

@Value("${fcm.project-id:}")
private String projectId;

// ============================================================
// DEPENDANCES
// ============================================================

private final UserRepo userRepo;

private final UserSessionRepository userSessionRepository;

private final ResourceLoader resourceLoader;

private final NotificationMessages notificationMessages;

// ============================================================
// CONSTRUCTEUR
// ============================================================

public FCMService(
        UserRepo userRepo,
        UserSessionRepository userSessionRepository,
        ResourceLoader resourceLoader,
        NotificationMessages notificationMessages) {

    this.userRepo = userRepo;
    this.userSessionRepository = userSessionRepository;
    this.resourceLoader = resourceLoader;
    this.notificationMessages = notificationMessages;
}

// ============================================================
// INITIALISATION FIREBASE
// ============================================================

/**
 * Initialise Firebase Admin SDK au démarrage de l'application.
 */
@PostConstruct
public void initialize() {

    if (serviceAccountKeyPath == null
            || serviceAccountKeyPath.isBlank()) {

        log.warn(
                "FCM désactivé : la propriété "
                        + "fcm.service-account-key n'est pas renseignée."
        );

        return;
    }

    String location = resolveResourceLocation();

    Resource resource = resourceLoader.getResource(location);

    if (!resource.exists() || !resource.isReadable()) {

        log.error(
                "FCM désactivé : clé Firebase illisible ou absente : '{}'",
                location
        );

        return;
    }

    try (InputStream keyStream = resource.getInputStream()) {

        GoogleCredentials credentials =
                GoogleCredentials.fromStream(keyStream);

        FirebaseOptions options =
                FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .setProjectId(projectId)
                        .build();

        initializeFirebaseApp(options);

        log.info(
                "Firebase initialisé pour le projet '{}'.",
                projectId
        );

    } catch (IOException e) {

        log.error(
                "FCM désactivé : impossible d'initialiser Firebase "
                        + "depuis '{}'.",
                location,
                e
        );
    }
}

/**
 * Résout le chemin du fichier Service Account.
 */
private String resolveResourceLocation() {

    if (serviceAccountKeyPath.contains(":")) {
        return serviceAccountKeyPath;
    }

    return ResourceLoader.CLASSPATH_URL_PREFIX
            + serviceAccountKeyPath;
}

/**
 * Initialise Firebase uniquement s'il n'est pas déjà initialisé.
 */
private void initializeFirebaseApp(FirebaseOptions options) {

    if (FirebaseApp.getApps().isEmpty()) {
        FirebaseApp.initializeApp(options);
    }
}

// ============================================================
// NOTIFICATION INDIVIDUELLE
// ============================================================

/**
 * Envoie une notification push à l'appareil actif de l'utilisateur.
 *
 * <p>Le titre et le corps arrivent deja rediges dans la langue de
 * {@code userId}: {@code NotificationService} les resout avant d'ecrire la
 * ligne en base, et transmet ici le meme texte. Les traduire une seconde
 * fois a cet endroit ferait diverger la notification poussee de celle que
 * l'utilisateur retrouvera dans sa boite.
 */
public void sendPushNotification(
        Long userId,
        NotificationDTO notificationDTO) {

    UserSession session =
            userSessionRepository.findById(userId).orElse(null);

    if (session == null
            || session.getFcmToken() == null
            || session.getFcmToken().isBlank()) {

        log.warn(
                "Impossible d'envoyer la notification : "
                        + "aucune session/token FCM pour userId={}",
                userId
        );

        return;
    }

    sendToToken(
            session.getFcmToken(),
            notificationDTO
    );
}

/**
 * Envoie une notification visible à un token FCM.
 *
 * Android :
 * - priorité HIGH
 * - notification visible
 * - son par défaut
 *
 * iOS :
 * - APNs alert
 * - priorité 10
 * - son par défaut
 */
private void sendToToken(
        String fcmToken,
        NotificationDTO notificationDTO) {

    if (fcmToken == null || fcmToken.isBlank()) {
        return;
    }

    if (FirebaseApp.getApps().isEmpty()) {

        log.error(
                "Impossible d'envoyer la notification : "
                        + "Firebase n'est pas initialisé."
        );

        return;
    }

    try {

        String title = getValueOrEmpty(
                notificationDTO.getTitle()
        );

        String body = getValueOrEmpty(
                notificationDTO.getMessage()
        );

        String type = getValueOrEmpty(
                notificationDTO.getType()
        );

        String relatedId =
                notificationDTO.getRelatedId() != null
                        ? notificationDTO.getRelatedId().toString()
                        : "";

        String notificationId =
                notificationDTO.getId() != null
                        ? notificationDTO.getId().toString()
                        : "";

        Message message =
                Message.builder()
                        .setToken(fcmToken)

                        // -------------------------------
                        // Notification générale FCM
                        // -------------------------------
                        .setNotification(
                                buildNotification(title, body)
                        )

                        // -------------------------------
                        // Android
                        // -------------------------------
                        .setAndroidConfig(
                                buildAndroidConfig()
                        )

                        // -------------------------------
                        // iOS / APNs
                        // -------------------------------
                        .setApnsConfig(
                                buildApnsAlertConfig(
                                        title,
                                        body
                                )
                        )

                        // -------------------------------
                        // Data Flutter
                        // -------------------------------
                        .putData("title", title)
                        .putData("body", body)
                        .putData("type", type)
                        .putData("relatedId", relatedId)
                        .putData("notificationId", notificationId)

                        .build();

        String messageId =
                FirebaseMessaging.getInstance().send(message);

        log.info(
                "Notification FCM envoyée avec succès. "
                        + "userToken={}... messageId={}",
                maskToken(fcmToken),
                messageId
        );

    } catch (FirebaseMessagingException e) {

        log.error(
                "Erreur lors de l'envoi de la notification FCM. "
                        + "Code={} Message={}",
                e.getMessagingErrorCode(),
                e.getMessage(),
                e
        );

    } catch (Exception e) {

        log.error(
                "Erreur inattendue lors de l'envoi FCM.",
                e
        );
    }
}

// ============================================================
// BROADCAST
// ============================================================

/**
 * Envoie une notification à tous les utilisateurs abonnés
 * au topic "all_users".
 */
public void sendBroadcastNotification(
        String title,
        String message) {

    if (FirebaseApp.getApps().isEmpty()) {

        log.error(
                "Impossible d'envoyer le broadcast : "
                        + "Firebase n'est pas initialisé."
        );

        return;
    }

    try {

        Message fcmMessage =
                Message.builder()

                        .setTopic(TOPIC_ALL_USERS)

                        // -------------------------------
                        // Notification générale
                        // -------------------------------
                        .setNotification(
                                buildNotification(title, message)
                        )

                        // -------------------------------
                        // Android
                        // -------------------------------
                        .setAndroidConfig(
                                buildAndroidConfig()
                        )

                        // -------------------------------
                        // iOS / APNs
                        // -------------------------------
                        .setApnsConfig(
                                buildApnsAlertConfig(
                                        title,
                                        message
                                )
                        )

                        // -------------------------------
                        // Data
                        // -------------------------------
                        .putData(
                                "type",
                                "BROADCAST"
                        )

                        .build();

        String messageId =
                FirebaseMessaging.getInstance()
                        .send(fcmMessage);

        log.info(
                "Broadcast FCM envoyé avec succès. messageId={}",
                messageId
        );

    } catch (FirebaseMessagingException e) {

        log.error(
                "Erreur lors de l'envoi broadcast FCM. "
                        + "Code={} Message={}",
                e.getMessagingErrorCode(),
                e.getMessage(),
                e
        );

    } catch (Exception e) {

        log.error(
                "Erreur inattendue lors de l'envoi broadcast FCM.",
                e
        );
    }
}

// ============================================================
// FORCE LOGOUT
// ============================================================

/**
 * Envoie une notification silencieuse pour forcer
 * la déconnexion d'un appareil.
 *
 * iOS :
 * - push-type = background
 * - priority = 5
 * - contentAvailable = true
 *
 * La notification n'est pas affichée à l'utilisateur.
 */
public void sendForceLogoutToToken(
        String fcmToken,
        String reason) {

    if (fcmToken == null || fcmToken.isBlank()) {
        return;
    }

    if (FirebaseApp.getApps().isEmpty()) {

        log.error(
                "Impossible d'envoyer le force logout : "
                        + "Firebase n'est pas initialisé."
        );

        return;
    }

    try {

        String logoutReason =
                reason == null || reason.isBlank()
                        ? "session_replaced"
                        : reason;

        Message message =
                Message.builder()

                        .setToken(fcmToken)

                        // -------------------------------
                        // Android
                        // -------------------------------
                        .setAndroidConfig(
                                AndroidConfig.builder()
                                        .setPriority(
                                                AndroidConfig.Priority.HIGH
                                        )
                                        .build()
                        )

                        // -------------------------------
                        // iOS / APNs Silent Push
                        // -------------------------------
                        .setApnsConfig(
                                buildApnsBackgroundConfig()
                        )

                        // -------------------------------
                        // Data
                        // -------------------------------
                        .putData(
                                "type",
                                "force_logout"
                        )
                        .putData(
                                "reason",
                                logoutReason
                        )

                        .build();

        String messageId =
                FirebaseMessaging.getInstance().send(message);

        log.info(
                "Push force logout envoyé. messageId={}",
                messageId
        );

    } catch (FirebaseMessagingException e) {

        log.error(
                "Erreur force logout FCM. "
                        + "Code={} Message={}",
                e.getMessagingErrorCode(),
                e.getMessage(),
                e
        );

    } catch (Exception e) {

        log.error(
                "Erreur inattendue force logout FCM.",
                e
        );
    }
}

// ============================================================
// TEST FCM
// ============================================================

/**
 * Teste la connexion FCM et envoie une notification visible
 * à un utilisateur.
 *
 * <p>Seule notification dont ce service redige lui-meme le texte: elle est
 * donc traduite ici, dans la langue du compte teste. Un test rendu en
 * francais a un utilisateur arabophone ne prouverait qu'a moitie que la
 * chaine fonctionne pour lui.
 */
public String testFcmConnection(Long userId) {

    // -------------------------------
    // Vérification Firebase
    // -------------------------------

    if (FirebaseApp.getApps().isEmpty()) {

        return "ERREUR: Firebase n'est pas initialisé. "
                + "Vérifiez le fichier service-account-key.json";
    }

    // -------------------------------
    // Récupération session
    // -------------------------------

    UserSession session =
            userSessionRepository.findById(userId).orElse(null);

    // -------------------------------
    // Récupération utilisateur
    // -------------------------------

    User user =
            userRepo.findById(userId).orElse(null);

    if (user == null) {

        return "ERREUR: Utilisateur avec ID "
                + userId
                + " non trouvé";
    }

    // -------------------------------
    // Récupération du token FCM
    // -------------------------------

    String fcmToken =
            getUserFcmToken(
                    session,
                    user
            );

    if (fcmToken == null || fcmToken.isBlank()) {

        return "ERREUR: L'utilisateur "
                + user.getUsername()
                + " n'a pas de token FCM.";
    }

    // -------------------------------
    // Envoi du test
    // -------------------------------

    try {

        NotificationMessages.Text text =
                notificationMessages.textFor(
                        "TEST",
                        SupportedLanguage.of(user)
                );

        final String title = "🧪 " + text.title();

        final String body = text.body();

        Message message =
                Message.builder()

                        .setToken(fcmToken)

                        // -------------------------------
                        // Notification générale
                        // -------------------------------
                        .setNotification(
                                buildNotification(
                                        title,
                                        body
                                )
                        )

                        // -------------------------------
                        // Android
                        // -------------------------------
                        .setAndroidConfig(
                                buildAndroidConfig()
                        )

                        // -------------------------------
                        // iOS / APNs
                        // -------------------------------
                        .setApnsConfig(
                                buildApnsAlertConfig(
                                        title,
                                        body
                                )
                        )

                        // -------------------------------
                        // Data
                        // -------------------------------
                        .putData(
                                "type",
                                "TEST"
                        )
                        .putData(
                                "timestamp",
                                String.valueOf(
                                        System.currentTimeMillis()
                                )
                        )

                        .build();

        String messageId =
                FirebaseMessaging.getInstance()
                        .send(message);

        return "SUCCÈS: Notification envoyée ! "
                + "Message ID: "
                + messageId
                + " | Utilisateur: "
                + user.getUsername()
                + " | Token: "
                + maskToken(fcmToken);

    } catch (FirebaseMessagingException e) {

        return "ERREUR FCM: "
                + e.getMessage()
                + " | Code: "
                + e.getMessagingErrorCode();

    } catch (Exception e) {

        return "ERREUR: "
                + e.getMessage();
    }
}

// ============================================================
// CONFIGURATION ANDROID
// ============================================================

/**
 * Construit la configuration Android commune
 * aux notifications visibles.
 */
private AndroidConfig buildAndroidConfig() {

    return AndroidConfig.builder()
            .setPriority(
                    AndroidConfig.Priority.HIGH
            )
            .setNotification(
                    AndroidNotification.builder()
                            .setChannelId(
                                    ANDROID_CHANNEL_ID
                            )
                            .setPriority(
                                    AndroidNotification.Priority.MAX
                            )
                            .setSound("default")
                            .setDefaultVibrateTimings(true)
                            .setDefaultLightSettings(true)
                            .setVisibility(
                                    AndroidNotification.Visibility.PUBLIC
                            )
                            .build()
            )
            .build();
}

// ============================================================
// CONFIGURATION APNs - NOTIFICATION VISIBLE
// ============================================================

/**
 * Construit la configuration APNs pour une notification
 * visible sur iOS.
 *
 * apns-push-type = alert
 * apns-priority = 10
 */
private ApnsConfig buildApnsAlertConfig(
        String title,
        String body) {

    return ApnsConfig.builder()

            .putHeader(
                    "apns-priority",
                    APNS_PRIORITY_ALERT
            )

            .putHeader(
                    "apns-push-type",
                    APNS_PUSH_TYPE_ALERT
            )

            .setAps(
                    Aps.builder()
                            .setAlert(
                                    ApsAlert.builder()
                                            .setTitle(title)
                                            .setBody(body)
                                            .build()
                            )
                            .setSound("default")
                            .build()
            )

            .build();
}

// ============================================================
// CONFIGURATION APNs - SILENT PUSH
// ============================================================

/**
 * Construit la configuration APNs pour une notification
 * silencieuse.
 *
 * apns-push-type = background
 * apns-priority = 5
 * contentAvailable = true
 */
private ApnsConfig buildApnsBackgroundConfig() {

    return ApnsConfig.builder()

            .putHeader(
                    "apns-priority",
                    APNS_PRIORITY_BACKGROUND
            )

            .putHeader(
                    "apns-push-type",
                    APNS_PUSH_TYPE_BACKGROUND
            )

            .setAps(
                    Aps.builder()
                            .setContentAvailable(true)
                            .build()
            )

            .build();
}

// ============================================================
// NOTIFICATION FCM
// ============================================================

/**
 * Construit la notification générale FCM.
 */
private Notification buildNotification(
        String title,
        String body) {

    return Notification.builder()
            .setTitle(
                    getValueOrEmpty(title)
            )
            .setBody(
                    getValueOrEmpty(body)
            )
            .build();
}

// ============================================================
// TOKEN FCM
// ============================================================

/**
 * Récupère le token FCM.
 *
 * Priorité :
 * 1. UserSession
 * 2. User
 */
private String getUserFcmToken(
        UserSession session,
        User user) {

    if (session != null
            && session.getFcmToken() != null
            && !session.getFcmToken().isBlank()) {

        return session.getFcmToken();
    }

    if (user.getFcmToken() != null
            && !user.getFcmToken().isBlank()) {

        return user.getFcmToken();
    }

    return null;
}

/**
 * Masque une partie du token FCM dans les logs.
 */
private String maskToken(String token) {

    if (token == null || token.isBlank()) {
        return "";
    }

    int visibleLength =
            Math.min(20, token.length());

    return token.substring(0, visibleLength)
            + "...";
}

// ============================================================
// UTILITAIRES
// ============================================================

/**
 * Retourne une chaîne vide lorsque la valeur est null.
 */
private String getValueOrEmpty(String value) {

    return value != null
            ? value
            : "";
}

// ============================================================
// STATUT FCM
// ============================================================

/**
 * Retourne le statut de la configuration Firebase/FCM.
 */
public String getFcmStatus() {

    StringBuilder status =
            new StringBuilder();

    status.append("=== Statut FCM ===\n");

    status.append("Firebase initialisé: ")
            .append(
                    !FirebaseApp.getApps().isEmpty()
            )
            .append("\n");

    status.append("Service Account Key: ")
            .append(serviceAccountKeyPath)
            .append("\n");

    status.append("Project ID: ")
            .append(projectId)
            .append("\n");

    if (!FirebaseApp.getApps().isEmpty()) {

        status.append("Firebase App Name: ")
                .append(
                        FirebaseApp
                                .getInstance()
                                .getName()
                )
                .append("\n");
    }

    return status.toString();
}


}
