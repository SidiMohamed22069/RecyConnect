package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.NegotiationDTO;
import com.project.RecyConnect.Model.Negotiation;
import com.project.RecyConnect.Model.NegotiationHistory;
import com.project.RecyConnect.Model.NegotiationStatus;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.NegotiationHistoryRepository;
import com.project.RecyConnect.Repository.NegotiationRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La contre-proposition du vendeur.
 *
 * <p>Troisieme reponse d'une negociation reelle, a cote d'accepter et de
 * refuser — et la plus frequente. Ce qui est verrouille ici: seul le vendeur
 * contre-propose, l'offre reste en attente (la balle repasse a l'acheteur), et
 * le fil garde les deux montants.
 */
@ExtendWith(MockitoExtension.class)
class NegotiationCounterOfferTest {

    @Mock private NegotiationRepository repo;
    @Mock private UserRepo userRepo;
    @Mock private ProductRepository productRepo;
    @Mock private NotificationService notificationService;
    @Mock private FileUrlService fileUrlService;
    @Mock private NegotiationHistoryRepository historyRepo;

    @InjectMocks private NegotiationService service;

    private User acheteur;
    private User vendeur;
    private Product annonce;

    @BeforeEach
    void setUp() {
        acheteur = User.builder().id(1L).username("Ahmed").build();
        vendeur = User.builder().id(2L).username("Fatima").build();
        annonce = Product.builder()
                .id(10L)
                .title("Cartons")
                .unit("kg")
                .quantityAvailable(100L)
                .user(vendeur)
                .build();
        lenient().when(fileUrlService.toPublicUrls(anyList())).thenReturn(java.util.List.of());
    }

    private Negotiation offre(String statut) {
        return Negotiation.builder()
                .id(100L)
                .sender(acheteur)
                .receiver(vendeur)
                .product(annonce)
                .status(statut)
                .price(20.0)
                .quantity(30)
                .build();
    }

    @Test
    @DisplayName("Le vendeur contre-propose: le prix change, l'offre reste en attente")
    void sellerCanCounterAndOfferStaysPending() {
        when(repo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_PENDING)));
        when(repo.save(any(Negotiation.class))).thenAnswer(i -> i.getArgument(0));

        NegotiationDTO result = service.counterBySeller(100L, 2L, 22.0, null);

        assertEquals(22.0, result.getPrice());
        assertEquals(30, result.getQuantity());
        assertEquals(NegotiationStatus.STATUS_PENDING, result.getStatus());
    }

    @Test
    @DisplayName("La contre-proposition est ajoutee au fil, sans effacer l'offre d'origine")
    void counterIsRecordedInTheThread() {
        when(repo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_PENDING)));
        when(repo.save(any(Negotiation.class))).thenAnswer(i -> i.getArgument(0));

        service.counterBySeller(100L, 2L, 22.0, null);

        ArgumentCaptor<NegotiationHistory> captor = ArgumentCaptor.forClass(NegotiationHistory.class);
        verify(historyRepo).save(captor.capture());
        assertEquals("COUNTER_OFFER", captor.getValue().getKind());
        assertEquals(22.0, captor.getValue().getPrice());
        assertEquals(2L, captor.getValue().getAuthor().getId());
    }

    @Test
    @DisplayName("L'acheteur ne contre-propose pas sur sa propre offre")
    void buyerCannotCounter() {
        when(repo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_PENDING)));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.counterBySeller(100L, 1L, 22.0, null));
        assertTrue(e.getMessage().contains("product owner"));
    }

    @Test
    @DisplayName("Une offre deja acceptee ne se contre-propose plus")
    void acceptedOfferCannotBeCountered() {
        when(repo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_ACCEPTED)));

        assertThrows(RuntimeException.class, () -> service.counterBySeller(100L, 2L, 22.0, null));
    }

    @Test
    @DisplayName("Une contre-proposition qui ne change rien est refusee")
    void counterMustChangeSomething() {
        when(repo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_PENDING)));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.counterBySeller(100L, 2L, null, null));
        assertTrue(e.getMessage().contains("change"));
    }

    @Test
    @DisplayName("Une contre-proposition ne peut pas depasser le stock restant")
    void counterCannotExceedStock() {
        when(repo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_PENDING)));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.counterBySeller(100L, 2L, null, 500));
        assertTrue(e.getMessage().contains("stock"));
    }

    @Test
    @DisplayName("Un prix negatif est refuse")
    void negativePriceIsRejected() {
        when(repo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_PENDING)));

        assertThrows(RuntimeException.class, () -> service.counterBySeller(100L, 2L, -5.0, null));
    }

    @Test
    @DisplayName("L'acheteur est prevenu de la contre-proposition")
    void buyerIsNotified() {
        when(repo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_PENDING)));
        when(repo.save(any(Negotiation.class))).thenAnswer(i -> i.getArgument(0));

        service.counterBySeller(100L, 2L, 22.0, null);

        // Le texte n'est plus compose ici : le service le redige dans la langue
        // du destinataire a partir de la cle OFFER_COUNTERED.
        verify(notificationService).sendLocalizedNotification(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq("OFFER_COUNTERED"),
                org.mockito.ArgumentMatchers.eq("Fatima"),
                org.mockito.ArgumentMatchers.eq(22.0),
                org.mockito.ArgumentMatchers.eq("Cartons"));
    }
}
