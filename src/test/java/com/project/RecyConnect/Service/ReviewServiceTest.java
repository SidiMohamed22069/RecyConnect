package com.project.RecyConnect.Service;

import com.project.RecyConnect.Model.Negotiation;
import com.project.RecyConnect.Model.NegotiationStatus;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.Review;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.NegotiationRepository;
import com.project.RecyConnect.Repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Qui a le droit de noter, et a quelles conditions.
 *
 * <p>Une note n'a de valeur que si elle vient de quelqu'un qui a reellement
 * traite avec le vendeur: sans ces verrous, il suffirait d'un compte et d'une
 * boucle pour enterrer un concurrent sous des notes de 1.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository repo;
    @Mock private NegotiationRepository negotiationRepo;

    @InjectMocks private ReviewService service;

    private User acheteur;
    private User vendeur;
    private User tiers;
    private Product annonce;

    @BeforeEach
    void setUp() {
        acheteur = User.builder().id(1L).username("Ahmed").build();
        vendeur = User.builder().id(2L).username("Fatima").build();
        tiers = User.builder().id(3L).username("Sidi").build();
        annonce = Product.builder().id(10L).title("Cartons").user(vendeur).build();
    }

    private Negotiation offre(String statut) {
        return Negotiation.builder()
                .id(100L)
                .sender(acheteur)
                .receiver(vendeur)
                .product(annonce)
                .status(statut)
                .price(20.0)
                .quantity(5)
                .build();
    }

    @Test
    @DisplayName("L'acheteur d'une offre acceptee peut noter le vendeur")
    void buyerOfAcceptedOfferCanReview() {
        when(negotiationRepo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_ACCEPTED)));
        when(repo.existsByNegotiationId(100L)).thenReturn(false);
        when(repo.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewService.ReviewResult result = service.create(100L, 1L, 5, "Rendez-vous tenu");

        assertEquals(ReviewService.Outcome.OK, result.outcome());
        assertEquals(2L, result.review().getTargetId());
        assertEquals(1L, result.review().getAuthorId());
        assertEquals(5, result.review().getRating());
    }

    @Test
    @DisplayName("Un tiers a l'offre ne peut pas noter")
    void strangerCannotReview() {
        when(negotiationRepo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_ACCEPTED)));

        ReviewService.ReviewResult result = service.create(100L, 3L, 1, null);

        assertEquals(ReviewService.Outcome.NOT_THE_BUYER, result.outcome());
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("Le vendeur ne note pas son acheteur: le sens est unique")
    void sellerCannotReviewBuyer() {
        when(negotiationRepo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_ACCEPTED)));

        assertEquals(ReviewService.Outcome.NOT_THE_BUYER,
                service.create(100L, 2L, 4, null).outcome());
    }

    @Test
    @DisplayName("Une offre encore en attente n'ouvre aucun droit de noter")
    void pendingOfferCannotBeReviewed() {
        when(negotiationRepo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_PENDING)));

        assertEquals(ReviewService.Outcome.NOT_ACCEPTED,
                service.create(100L, 1L, 5, null).outcome());
    }

    @Test
    @DisplayName("Une transaction ne se note qu'une fois")
    void oneReviewPerTransaction() {
        when(negotiationRepo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_ACCEPTED)));
        when(repo.existsByNegotiationId(100L)).thenReturn(true);

        assertEquals(ReviewService.Outcome.ALREADY_REVIEWED,
                service.create(100L, 1L, 5, null).outcome());
    }

    @Test
    @DisplayName("Une note hors de l'echelle de 1 a 5 est refusee")
    void ratingMustBeWithinScale() {
        assertEquals(ReviewService.Outcome.INVALID_RATING, service.create(100L, 1L, 0, null).outcome());
        assertEquals(ReviewService.Outcome.INVALID_RATING, service.create(100L, 1L, 6, null).outcome());
        assertEquals(ReviewService.Outcome.INVALID_RATING, service.create(100L, 1L, null, null).outcome());
        verify(negotiationRepo, never()).findById(any());
    }

    @Test
    @DisplayName("Un commentaire vide n'est pas enregistre comme une chaine vide")
    void blankCommentBecomesNull() {
        when(negotiationRepo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_ACCEPTED)));
        when(repo.existsByNegotiationId(100L)).thenReturn(false);
        when(repo.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertNull(service.create(100L, 1L, 4, "   ").review().getComment());
    }

    @Test
    @DisplayName("La moyenne reste nulle tant qu'aucun avis n'existe")
    void averageIsNullWithoutAnyReview() {
        when(repo.averageRatingByTargetId(2L)).thenReturn(null);
        when(repo.countByTargetId(2L)).thenReturn(0L);

        assertNull(service.summaryFor(2L).getAverage());
        assertEquals(0L, service.summaryFor(2L).getCount());
    }
}
