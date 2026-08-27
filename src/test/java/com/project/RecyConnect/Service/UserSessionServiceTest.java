package com.project.RecyConnect.Service;

import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Model.UserSession;
import com.project.RecyConnect.Repository.UserRepo;
import com.project.RecyConnect.Repository.UserSessionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Le remplacement de session, cote jeton de rafraichissement.
 *
 * <p>Le modele n'autorise qu'une session par compte: se connecter ailleurs
 * deconnecte l'appareil precedent. Un jeton de rafraichissement qui survivrait
 * a ce remplacement rendrait la deconnexion illusoire — l'ancien appareil se
 * refabriquerait un jeton d'acces valide en une requete, sans mot de passe.
 */
@ExtendWith(MockitoExtension.class)
class UserSessionServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private UserSessionRepository userSessionRepository;
    @Mock private UserRepo userRepo;
    @Mock private EntityManager entityManager;

    private UserSessionService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new UserSessionService(userSessionRepository, userRepo);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        user = User.builder().id(USER_ID).username("Fatima").role(Role.USER).build();
    }

    @Test
    @DisplayName("se connecter ailleurs invalide le jeton de rafraichissement precedent")
    void leRemplacementEffaceLeJeton() {
        UserSession existante = UserSession.builder()
                .userId(USER_ID)
                .deviceId("appareil-a")
                .fcmToken("fcm-a")
                .sessionVersion(3L)
                .refreshTokenHash("empreinte-de-l-ancien-jeton")
                .refreshTokenExpiresAt(OffsetDateTime.now().plusDays(30))
                .build();

        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
        when(entityManager.find(UserSession.class, USER_ID)).thenReturn(existante);

        UserSessionService.SessionReplacementResult result =
                service.replaceSession(USER_ID, "appareil-b", "Telephone B", "fcm-b");

        UserSession session = result.session();
        assertNull(session.getRefreshTokenHash(),
                "l'ancien jeton doit mourir avec la session qu'il prolongeait");
        assertNull(session.getRefreshTokenExpiresAt());
        assertEquals(4L, session.getSessionVersion());
        assertEquals("appareil-b", session.getDeviceId());
        assertEquals("fcm-a", result.previousFcmToken(),
                "l'ancien appareil doit encore etre joignable pour la notification de deconnexion");
    }

    @Test
    @DisplayName("une premiere session part sans jeton de rafraichissement")
    void unePremiereSessionNaPasDeJeton() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
        when(entityManager.find(UserSession.class, USER_ID)).thenReturn(null);

        UserSession session = service
                .replaceSession(USER_ID, "appareil-a", "Telephone A", "fcm-a")
                .session();

        // Il est emis juste apres, par RefreshTokenService.
        assertNull(session.getRefreshTokenHash());
        assertEquals(1L, session.getSessionVersion());
    }
}
