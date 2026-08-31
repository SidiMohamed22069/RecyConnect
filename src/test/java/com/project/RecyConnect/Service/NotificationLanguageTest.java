package com.project.RecyConnect.Service;

import com.project.RecyConnect.Config.NotificationMessagesConfig;
import com.project.RecyConnect.DTO.NotificationDTO;
import com.project.RecyConnect.Model.Notification;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.NotificationRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dans quelle langue part une notification, et pour qui.
 *
 * <p>La regle verifiee ici est celle qui manquait: le texte suit la preference
 * du DESTINATAIRE, jamais celle de l'auteur de l'action. C'est ce que l'ancien
 * dispositif ne pouvait pas respecter — {@code NegotiationService} redigeait le
 * titre et le corps alors qu'il ne connaissait du destinataire que son
 * identifiant, et ecrivait donc du francais pour tout le monde.
 *
 * <p>Le second point verifie est que la ligne ecrite en base et la poussee FCM
 * portent le meme texte: elles sont censees etre la meme notification, vue de
 * deux endroits.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationLanguageTest {

    @Mock private NotificationRepository repo;
    @Mock private UserRepo userRepo;
    @Mock private FCMService fcmService;
    @Mock private WebSocketService webSocketService;
    @Mock private UserSessionManager sessionManager;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        NotificationMessages messages = new NotificationMessages(
                new NotificationMessagesConfig().notificationMessageSource());

        service = new NotificationService(repo, userRepo, fcmService,
                webSocketService, sessionManager, messages);

        when(repo.save(any(Notification.class))).thenAnswer(call -> call.getArgument(0));
    }

    /** Enregistre un compte que le service retrouvera par son identifiant. */
    private User compte(Long id, String nom, String langue) {
        User user = User.builder().id(id).username(nom).preferredLanguage(langue).build();
        when(userRepo.findById(id)).thenReturn(Optional.of(user));
        return user;
    }

    /** La ligne effectivement ecrite en base. */
    private Notification enregistree() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repo).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("un destinataire arabophone recoit sa notification en arabe")
    void destinataireArabophone() {
        compte(1L, "Ahmed", "fr");
        compte(2L, "Fatima", "ar");

        service.sendLocalizedNotification(2L, 1L, 100L, "OFFER_ACCEPTED", "Cartons");

        Notification ecrite = enregistree();
        assertEquals("تم قبول العرض", ecrite.getTitle());
        assertEquals("تم قبول عرضك على Cartons", ecrite.getMessage());
    }

    @Test
    @DisplayName("un destinataire anglophone recoit sa notification en anglais")
    void destinataireAnglophone() {
        compte(1L, "Ahmed", "ar");
        compte(2L, "John", "en");

        service.sendLocalizedNotification(2L, 1L, 100L, "OFFER_ACCEPTED", "Cartons");

        Notification ecrite = enregistree();
        assertEquals("Offer accepted", ecrite.getTitle());
        assertEquals("Your offer on Cartons was accepted", ecrite.getMessage());
    }

    @Test
    @DisplayName("c'est la langue du destinataire qui compte, pas celle de l'expediteur")
    void langueDeLExpediteurIgnoree() {
        compte(1L, "Ahmed", "ar");
        compte(2L, "John", "en");

        service.sendLocalizedNotification(2L, 1L, 100L, "OFFER_RECEIVED", "Ahmed", "Cartons");

        Notification ecrite = enregistree();
        assertEquals("New offer received", ecrite.getTitle());
        assertEquals("Ahmed made an offer on: Cartons", ecrite.getMessage());
    }

    @Test
    @DisplayName("sans preference enregistree, le francais: les comptes existants ne changent pas de langue")
    void langueAbsenteRetombeSurLeFrancais() {
        compte(1L, "Ahmed", "ar");
        compte(2L, "Fatima", null);

        service.sendLocalizedNotification(2L, 1L, 100L, "OFFER_ACCEPTED", "Cartons");

        Notification ecrite = enregistree();
        assertEquals("Offre acceptée", ecrite.getTitle());
        assertEquals("Votre offre sur Cartons a été acceptée", ecrite.getMessage());
    }

    @Test
    @DisplayName("une langue invalide en base retombe aussi sur le francais")
    void langueInvalideRetombeSurLeFrancais() {
        compte(1L, "Ahmed", "en");
        compte(2L, "Fatima", "klingon");

        service.sendLocalizedNotification(2L, 1L, 100L, "OFFER_ACCEPTED", "Cartons");

        assertEquals("Offre acceptée", enregistree().getTitle());
    }

    @Test
    @DisplayName("un destinataire disparu n'interrompt pas la chaine")
    void destinataireIntrouvable() {
        when(userRepo.findById(404L)).thenReturn(Optional.empty());
        compte(1L, "Ahmed", "ar");

        service.sendLocalizedNotification(404L, 1L, 100L, "OFFER_ACCEPTED", "Cartons");

        assertEquals("Offre acceptée", enregistree().getTitle());
    }

    @Test
    @DisplayName("la poussee FCM porte exactement le texte enregistre en base")
    void fcmRecoitLeMemeTexte() {
        compte(1L, "Ahmed", "fr");
        compte(2L, "Fatima", "ar");
        when(sessionManager.isUserConnected(2L)).thenReturn(false);

        service.sendLocalizedNotification(2L, 1L, 100L, "OFFER_ACCEPTED", "Cartons");

        ArgumentCaptor<NotificationDTO> pousse = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(fcmService).sendPushNotification(eq(2L), pousse.capture());

        Notification ecrite = enregistree();
        assertEquals(ecrite.getTitle(), pousse.getValue().getTitle());
        assertEquals(ecrite.getMessage(), pousse.getValue().getMessage());
        assertEquals("تم قبول العرض", pousse.getValue().getTitle());
    }

    @Test
    @DisplayName("le WebSocket aussi, quand le destinataire est connecte")
    void webSocketRecoitLeMemeTexte() {
        compte(1L, "Ahmed", "fr");
        compte(2L, "John", "en");
        when(sessionManager.isUserConnected(2L)).thenReturn(true);

        service.sendLocalizedNotification(2L, 1L, 100L, "OFFER_ACCEPTED", "Cartons");

        ArgumentCaptor<NotificationDTO> envoye = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(webSocketService).sendToUser(eq(2L), envoye.capture());

        assertEquals("Offer accepted", envoye.getValue().getTitle());
    }

    @Test
    @DisplayName("sendOfferNotification traduit le nom de l'expediteur ET le libelle")
    void nouvelleOffre() {
        compte(1L, "Ahmed", "fr");
        compte(2L, "Fatima", "ar");

        service.sendOfferNotification(2L, 1L, 100L, "Cartons");

        Notification ecrite = enregistree();
        assertEquals("عرض جديد", ecrite.getTitle());
        assertEquals("قدّم Ahmed عرضًا على: Cartons", ecrite.getMessage());
        assertEquals("OFFER_RECEIVED", ecrite.getType());
    }

    @Test
    @DisplayName("sendRefusalNotification s'adresse a l'auteur de l'offre, dans SA langue")
    void offreRefusee() {
        // 1 a fait l'offre et recoit le refus; 2 a refuse.
        compte(1L, "John", "en");
        compte(2L, "Fatima", "ar");

        service.sendRefusalNotification(1L, 2L, 100L, "Cartons");

        Notification ecrite = enregistree();
        assertEquals("Offer declined", ecrite.getTitle());
        assertEquals("Fatima declined your offer on: Cartons", ecrite.getMessage());
    }

    @Test
    @DisplayName("un expediteur introuvable donne un nom generique traduit, pas \"null\"")
    void expediteurIntrouvable() {
        when(userRepo.findById(404L)).thenReturn(Optional.empty());
        compte(2L, "Fatima", "ar");

        service.sendOfferNotification(2L, 404L, 100L, "Cartons");

        Notification ecrite = enregistree();
        assertFalse(ecrite.getMessage().contains("null"));
        assertEquals("قدّم مستخدم عرضًا على: Cartons", ecrite.getMessage());
    }

    @Test
    @DisplayName("un titre d'annonce absent devient un terme traduit")
    void annonceSansTitre() {
        compte(1L, "Ahmed", "fr");
        compte(2L, "John", "en");

        service.sendLocalizedNotification(2L, 1L, null, "QUEUE_UPDATED", (Object) null);

        Notification ecrite = enregistree();
        assertFalse(ecrite.getMessage().contains("null"));
        assertEquals("The offer queue was updated for an item", ecrite.getMessage());
    }
}
