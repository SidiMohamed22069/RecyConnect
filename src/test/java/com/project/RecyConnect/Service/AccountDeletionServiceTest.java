package com.project.RecyConnect.Service;

import com.project.RecyConnect.Model.*;
import com.project.RecyConnect.Repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * La suppression de compte exigee par le reglement "Donnees utilisateur" de
 * Google Play.
 *
 * <p>Ce qui est verrouille ici : elle emporte reellement tout ce qui pointe
 * vers le compte — la cascade de l'entite {@code User} n'en couvrait qu'une
 * partie, et les tables restantes faisaient echouer la requete — et elle
 * n'efface pas les signalements, qu'elle se contente de detacher.
 */
@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private UserRepo userRepo;
    @Mock private ProductRepository productRepo;
    @Mock private NegotiationRepository negotiationRepo;
    @Mock private NotificationRepository notificationRepo;
    @Mock private PhoneVerificationRepository phoneVerificationRepo;
    @Mock private UserSessionRepository sessionRepo;
    @Mock private UserBlockRepository blockRepo;
    @Mock private ReportRepository reportRepo;
    @Mock private UploadedFileStore fileStore;

    @InjectMocks private AccountDeletionService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).username("Ahmed").phone(22345678L).role(Role.USER).build();
    }

    private void stubCompteVide() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
        when(notificationRepo.findBySenderId(USER_ID)).thenReturn(List.of());
        when(notificationRepo.findByReceiverId(USER_ID)).thenReturn(List.of());
        when(phoneVerificationRepo.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(phoneVerificationRepo.findByPhoneOrderByCreatedAtDesc(22345678L)).thenReturn(List.of());
        when(negotiationRepo.findBySenderId(USER_ID)).thenReturn(List.of());
        when(negotiationRepo.findByReceiverId(USER_ID)).thenReturn(List.of());
        when(productRepo.findByUserId(USER_ID)).thenReturn(List.of());
        when(sessionRepo.findById(USER_ID)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("un compte inconnu ne se supprime pas en silence")
    void refuseUnCompteInconnu() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.deleteAccount(USER_ID));
        verify(userRepo, never()).deleteById(any());
    }

    @Test
    @DisplayName("tout ce qui pointe vers le compte part avec lui")
    void effaceLesTablesLiees() {
        stubCompteVide();

        service.deleteAccount(USER_ID);

        // Trois tables que la cascade de l'entite User ne couvrait pas, et dont
        // la contrainte d'integrite faisait echouer la suppression.
        verify(notificationRepo, times(2)).deleteAll(anyList());
        verify(phoneVerificationRepo).findByPhoneOrderByCreatedAtDesc(22345678L);
        verify(sessionRepo).findById(USER_ID);
        verify(blockRepo).deleteAllInvolving(USER_ID);
        verify(userRepo).deleteById(USER_ID);
    }

    @Test
    @DisplayName("les signalements sont detaches, jamais effaces")
    void detacheLesSignalements() {
        stubCompteVide();

        service.deleteAccount(USER_ID);

        verify(reportRepo).detachReporter(USER_ID);
        verify(reportRepo).detachHandler(USER_ID);
        verify(reportRepo, never()).deleteAll(anyList());
        verify(reportRepo, never()).deleteById(any());
    }

    @Test
    @DisplayName("les photos des annonces quittent le disque")
    void effaceLesPhotos() {
        Product annonce = Product.builder()
                .id(3L)
                .user(user)
                .imageUrls(List.of("https://exemple.mr/api/files/a.jpg",
                                   "https://exemple.mr/api/files/b.jpg"))
                .build();
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
        when(notificationRepo.findBySenderId(USER_ID)).thenReturn(List.of());
        when(notificationRepo.findByReceiverId(USER_ID)).thenReturn(List.of());
        when(phoneVerificationRepo.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(phoneVerificationRepo.findByPhoneOrderByCreatedAtDesc(22345678L)).thenReturn(List.of());
        when(negotiationRepo.findBySenderId(USER_ID)).thenReturn(List.of());
        when(negotiationRepo.findByReceiverId(USER_ID)).thenReturn(List.of());
        when(productRepo.findByUserId(USER_ID)).thenReturn(List.of(annonce));
        when(negotiationRepo.findByProductId(3L)).thenReturn(List.of());
        when(sessionRepo.findById(USER_ID)).thenReturn(Optional.empty());

        service.deleteAccount(USER_ID);

        // Une photo laissee sur le disque resterait accessible par son URL.
        verify(fileStore).deleteAllByPublicUrl(List.of(
                "https://exemple.mr/api/files/a.jpg",
                "https://exemple.mr/api/files/b.jpg"));
        verify(productRepo).deleteAll(List.of(annonce));
    }

    @Test
    @DisplayName("les offres portant sur une annonce partent avant elle")
    void effaceLesOffresDesAnnonces() {
        Product annonce = Product.builder().id(3L).user(user).imageUrls(List.of()).build();
        Negotiation offre = Negotiation.builder().id(11L).product(annonce).build();
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
        when(notificationRepo.findBySenderId(USER_ID)).thenReturn(List.of());
        when(notificationRepo.findByReceiverId(USER_ID)).thenReturn(List.of());
        when(phoneVerificationRepo.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(phoneVerificationRepo.findByPhoneOrderByCreatedAtDesc(22345678L)).thenReturn(List.of());
        when(negotiationRepo.findBySenderId(USER_ID)).thenReturn(List.of());
        when(negotiationRepo.findByReceiverId(USER_ID)).thenReturn(List.of());
        when(productRepo.findByUserId(USER_ID)).thenReturn(List.of(annonce));
        when(negotiationRepo.findByProductId(3L)).thenReturn(List.of(offre));
        when(sessionRepo.findById(USER_ID)).thenReturn(Optional.empty());

        service.deleteAccount(USER_ID);

        InOrderVerification.offresAvantAnnonces(negotiationRepo, productRepo, offre, annonce);
    }

    /** Regroupe la verification d'ordre, illisible en ligne. */
    static final class InOrderVerification {
        static void offresAvantAnnonces(NegotiationRepository negotiationRepo,
                                        ProductRepository productRepo,
                                        Negotiation offre,
                                        Product annonce) {
            org.mockito.InOrder ordre = inOrder(negotiationRepo, productRepo);
            ordre.verify(negotiationRepo).deleteAll(eq(List.of(offre)));
            ordre.verify(productRepo).deleteAll(eq(List.of(annonce)));
        }
    }
}
