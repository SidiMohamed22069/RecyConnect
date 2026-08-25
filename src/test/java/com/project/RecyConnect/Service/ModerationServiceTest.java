package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.ReportDTO;
import com.project.RecyConnect.Model.*;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.ReportRepository;
import com.project.RecyConnect.Repository.UserBlockRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Blocage et signalement : les deux dispositifs que Google Play exige d'une
 * application qui publie du contenu d'utilisateurs.
 *
 * <p>Ce qui est verrouille ici : un blocage rejoue ne cree pas de doublon, il
 * masque dans les deux sens, et un signalement ne peut ni viser son auteur ni
 * porter un motif invente.
 */
@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock private UserBlockRepository blockRepo;
    @Mock private ReportRepository reportRepo;
    @Mock private UserRepo userRepo;
    @Mock private ProductRepository productRepo;

    @InjectMocks private ModerationService service;

    private User ahmed;
    private User fatima;

    @BeforeEach
    void setUp() {
        ahmed = User.builder().id(1L).username("Ahmed").role(Role.USER).build();
        fatima = User.builder().id(2L).username("Fatima").role(Role.USER).build();
    }

    // ---------------------------------------------------------------- blocage

    @Test
    @DisplayName("bloquer enregistre la paire")
    void bloqueUnUtilisateur() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(fatima));
        when(blockRepo.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(false);

        service.block(ahmed, 2L);

        ArgumentCaptor<UserBlock> saved = ArgumentCaptor.forClass(UserBlock.class);
        verify(blockRepo).save(saved.capture());
        assertEquals(1L, saved.getValue().getBlocker().getId());
        assertEquals(2L, saved.getValue().getBlocked().getId());
    }

    @Test
    @DisplayName("bloquer deux fois ne cree pas de doublon")
    void bloquerDeuxFoisEstSansEffet() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(fatima));
        when(blockRepo.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);

        service.block(ahmed, 2L);

        verify(blockRepo, never()).save(any());
    }

    @Test
    @DisplayName("on ne peut pas se bloquer soi-meme")
    void refuseLAutoBlocage() {
        assertThrows(IllegalArgumentException.class, () -> service.block(ahmed, 1L));
        verify(blockRepo, never()).save(any());
    }

    @Test
    @DisplayName("bloquer un compte inexistant est refuse")
    void refuseUneCibleInconnue() {
        when(userRepo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.block(ahmed, 99L));
    }

    @Test
    @DisplayName("le masquage joue dans les deux sens")
    void masqueDansLesDeuxSens() {
        when(blockRepo.findHiddenUserIds(1L)).thenReturn(List.of(2L, 3L));

        Set<Long> hidden = service.hiddenUserIds(1L);

        assertEquals(Set.of(2L, 3L), hidden);
    }

    @Test
    @DisplayName("un visiteur sans compte n'a personne a masquer")
    void aucunMasquageSansCompte() {
        assertTrue(service.hiddenUserIds(null).isEmpty());
        verify(blockRepo, never()).findHiddenUserIds(any());
    }

    @Test
    @DisplayName("isBlockedBetween interroge les deux sens")
    void detecteLeBlocageInverse() {
        when(blockRepo.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(false);
        when(blockRepo.existsByBlockerIdAndBlockedId(2L, 1L)).thenReturn(true);

        assertTrue(service.isBlockedBetween(1L, 2L));
    }

    // ------------------------------------------------------------ signalement

    @Test
    @DisplayName("signaler une annonce releve son auteur")
    void signaleUneAnnonce() {
        Product annonce = Product.builder().id(10L).user(fatima).build();
        when(productRepo.findById(10L)).thenReturn(Optional.of(annonce));
        when(reportRepo.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
                1L, "PRODUCT", 10L, ReportStatus.PENDING)).thenReturn(false);
        when(reportRepo.save(any(Report.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepo.findById(2L)).thenReturn(Optional.of(fatima));

        ReportDTO request = new ReportDTO();
        request.setTargetType("product");
        request.setTargetId(10L);
        request.setReason("scam");
        request.setDetails("  photos qui ne correspondent pas  ");

        ReportDTO created = service.report(ahmed, request);

        assertEquals("PRODUCT", created.getTargetType());
        assertEquals("SCAM", created.getReason());
        assertEquals(2L, created.getTargetUserId(),
                "l'auteur du contenu doit etre releve au depot, l'annonce pouvant disparaitre");
        assertEquals("photos qui ne correspondent pas", created.getDetails());
        assertEquals(ReportStatus.PENDING, created.getStatus());
        assertEquals(1L, created.getReporterId());
    }

    @Test
    @DisplayName("un motif inconnu est refuse")
    void refuseUnMotifInvente() {
        ReportDTO request = new ReportDTO();
        request.setTargetType("PRODUCT");
        request.setTargetId(10L);
        request.setReason("PARCE_QUE");

        assertThrows(IllegalArgumentException.class, () -> service.report(ahmed, request));
        verify(reportRepo, never()).save(any());
    }

    @Test
    @DisplayName("on ne signale pas sa propre annonce")
    void refuseDeSignalerSonProprContenu() {
        Product annonce = Product.builder().id(10L).user(ahmed).build();
        when(productRepo.findById(10L)).thenReturn(Optional.of(annonce));

        ReportDTO request = new ReportDTO();
        request.setTargetType("PRODUCT");
        request.setTargetId(10L);
        request.setReason("SPAM");

        assertThrows(IllegalArgumentException.class, () -> service.report(ahmed, request));
        verify(reportRepo, never()).save(any());
    }

    @Test
    @DisplayName("un second signalement du meme contenu n'encombre pas la file")
    void neDoublonnePasLaFile() {
        Product annonce = Product.builder().id(10L).user(fatima).build();
        Report existant = Report.builder()
                .id(5L).reporter(ahmed).targetType("PRODUCT").targetId(10L)
                .targetUserId(2L).reason("SPAM").status(ReportStatus.PENDING).build();
        when(productRepo.findById(10L)).thenReturn(Optional.of(annonce));
        when(reportRepo.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
                1L, "PRODUCT", 10L, ReportStatus.PENDING)).thenReturn(true);
        when(reportRepo.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("PRODUCT", 10L))
                .thenReturn(List.of(existant));

        ReportDTO request = new ReportDTO();
        request.setTargetType("PRODUCT");
        request.setTargetId(10L);
        request.setReason("SPAM");

        ReportDTO rendu = service.report(ahmed, request);

        assertEquals(5L, rendu.getId());
        verify(reportRepo, never()).save(any());
    }

    @Test
    @DisplayName("traiter un signalement consigne qui, quand et quoi")
    void consigneLeTraitement() {
        User moderateur = User.builder().id(9L).username("Moderation").role(Role.ADMIN).build();
        Report report = Report.builder()
                .id(5L).reporter(ahmed).targetType("PRODUCT").targetId(10L)
                .targetUserId(2L).reason("SPAM").status(ReportStatus.PENDING).build();
        when(reportRepo.findById(5L)).thenReturn(Optional.of(report));
        when(reportRepo.save(any(Report.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepo.findById(2L)).thenReturn(Optional.of(fatima));

        ReportDTO handled = service.handle(5L, ReportStatus.ACTIONED, "Annonce retiree", moderateur);

        assertEquals(ReportStatus.ACTIONED, handled.getStatus());
        assertEquals("Annonce retiree", handled.getResolution());
        assertEquals("Moderation", handled.getHandledByName());
        assertNotNull(handled.getHandledAt());
    }

    @Test
    @DisplayName("remettre un signalement en attente efface sa date de traitement")
    void reouvreUnSignalement() {
        User moderateur = User.builder().id(9L).username("Moderation").role(Role.ADMIN).build();
        Report report = Report.builder()
                .id(5L).reporter(ahmed).targetType("USER").targetId(2L)
                .targetUserId(2L).reason("OFFENSIVE").status(ReportStatus.ACTIONED).build();
        when(reportRepo.findById(5L)).thenReturn(Optional.of(report));
        when(reportRepo.save(any(Report.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepo.findById(2L)).thenReturn(Optional.of(fatima));

        ReportDTO handled = service.handle(5L, ReportStatus.PENDING, null, moderateur);

        assertNull(handled.getHandledAt());
    }
}
