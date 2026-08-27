package com.project.RecyConnect.Service;

import com.project.RecyConnect.Model.UserSession;
import com.project.RecyConnect.Repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le renouvellement de session (point H4 de l'audit mobile).
 *
 * <p>Ce qui est verrouille ici tient a ce qu'un jeton de rafraichissement est
 * un secret de longue duree, equivalent a un mot de passe: il ne doit jamais
 * atterrir en clair en base, ne doit pas resservir apres usage, ne doit pas
 * survivre a la session qui l'a emis, et ne doit rien ouvrir depuis un autre
 * appareil. Chacune de ces regles, prise a l'envers, transforme la correction
 * d'un defaut d'ergonomie en trou de securite.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Long USER_ID = 7L;
    private static final String DEVICE = "appareil-a";

    @Mock private UserSessionRepository sessionRepository;

    private RefreshTokenService service;
    private UserSession session;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(sessionRepository, 30L);
        session = UserSession.builder()
                .userId(USER_ID)
                .deviceId(DEVICE)
                .deviceName("Telephone de Fatima")
                .fcmToken("fcm-1")
                .sessionVersion(3L)
                .build();
    }

    /** Emet un jeton et rend sa valeur en clair, comme le ferait la connexion. */
    private String emettre() {
        when(sessionRepository.findById(USER_ID)).thenReturn(Optional.of(session));
        return service.issueFor(USER_ID);
    }

    /** Branche la recherche par empreinte sur la session courante. */
    private void resoudreParEmpreinte() {
        when(sessionRepository.findByRefreshTokenHash(any()))
                .thenAnswer(invocation -> {
                    String demande = invocation.getArgument(0);
                    return demande.equals(session.getRefreshTokenHash())
                            ? Optional.of(session)
                            : Optional.empty();
                });
    }

    @Test
    @DisplayName("le jeton emis n'est jamais ecrit en clair en base")
    void leJetonNestPasStockeEnClair() {
        String raw = emettre();

        ArgumentCaptor<UserSession> saved = ArgumentCaptor.forClass(UserSession.class);
        verify(sessionRepository).save(saved.capture());

        String stocke = saved.getValue().getRefreshTokenHash();
        assertNotEquals(raw, stocke, "le jeton en clair ne doit pas atteindre la base");
        assertEquals(64, stocke.length(), "empreinte SHA-256 en hexadecimal");
        assertEquals(RefreshTokenService.hash(raw), stocke);
        assertTrue(saved.getValue().getRefreshTokenExpiresAt().isAfter(OffsetDateTime.now()));
    }

    @Test
    @DisplayName("deux emissions ne rendent jamais le meme jeton")
    void chaqueJetonEstUnique() {
        String premier = emettre();
        String second = emettre();

        assertNotEquals(premier, second);
    }

    @Test
    @DisplayName("un jeton valide est echange contre un nouveau, sur le meme appareil")
    void unJetonValideEstRenouvele() {
        String raw = emettre();
        resoudreParEmpreinte();

        RefreshTokenService.RefreshOutcome outcome = service.rotate(raw, DEVICE).orElseThrow();

        assertEquals(USER_ID, outcome.userId());
        // La session est prolongee, pas remplacee: faire tourner sessionVersion
        // couperait le WebSocket et les jetons encore en vol de cet appareil.
        assertEquals(3L, outcome.sessionVersion());
        assertEquals(DEVICE, outcome.deviceId());
        assertNotEquals(raw, outcome.refreshToken());
    }

    @Test
    @DisplayName("le jeton presente ne resservira pas")
    void leJetonNeSertQuUneFois() {
        String raw = emettre();
        resoudreParEmpreinte();

        service.rotate(raw, DEVICE).orElseThrow();

        // Rejouer un jeton intercepte ne doit rien ouvrir.
        assertTrue(service.rotate(raw, DEVICE).isEmpty());
    }

    @Test
    @DisplayName("un jeton perime est refuse et efface de la base")
    void unJetonPerimeEstRefuse() {
        String raw = emettre();
        session.setRefreshTokenExpiresAt(OffsetDateTime.now().minusMinutes(1));
        resoudreParEmpreinte();

        assertTrue(service.rotate(raw, DEVICE).isEmpty());
        // Le laisser en base en ferait une cible sans contrepartie.
        assertNull(session.getRefreshTokenHash());
        assertNull(session.getRefreshTokenExpiresAt());
    }

    @Test
    @DisplayName("un autre appareil ne renouvelle rien, meme avec le bon jeton")
    void unAutreAppareilEstRefuse() {
        String raw = emettre();
        resoudreParEmpreinte();

        // Modele mono-appareil: voler le jeton ne suffit pas, il faut aussi
        // etre l'appareil enregistre sur la session.
        assertTrue(service.rotate(raw, "appareil-b").isEmpty());
    }

    @Test
    @DisplayName("sans en-tete d'appareil, rien n'est renouvele ni meme cherche")
    void sansAppareilRienNestCherche() {
        String raw = emettre();

        assertTrue(service.rotate(raw, null).isEmpty());
        assertTrue(service.rotate(raw, "  ").isEmpty());
        verify(sessionRepository, never()).findByRefreshTokenHash(any());
    }

    @Test
    @DisplayName("un jeton inconnu est refuse sans rien reveler")
    void unJetonInconnuEstRefuse() {
        emettre();
        resoudreParEmpreinte();

        assertTrue(service.rotate("jeton-invente", DEVICE).isEmpty());
    }

    @Test
    @DisplayName("un compte sans session ouverte n'obtient pas de jeton")
    void sansSessionPasDeJeton() {
        when(sessionRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Inscription ou connexion sans informations d'appareil: il n'y a pas
        // de session unique, donc rien a rafraichir.
        assertNull(service.issueFor(USER_ID));
        verify(sessionRepository, never()).save(any());
    }
}
