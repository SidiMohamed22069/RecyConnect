package com.project.RecyConnect.Service;

import com.project.RecyConnect.Model.*;
import com.project.RecyConnect.Repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suppression de compte et d'annonce contre une vraie base (H2), avec les
 * tables v2 qui referencent offres, comptes et annonces sans cascade :
 * favoris, avis, veilles, fil des negociations. Le test Mockito ne pouvait
 * pas voir la violation de contrainte qui faisait echouer la requete.
 */
@SpringBootTest
@Transactional
class AccountDeletionIntegrationTest {

    @Autowired AccountDeletionService accountDeletion;
    @Autowired ProductDeletionService productDeletion;
    @Autowired UserRepo users;
    @Autowired ProductRepository products;
    @Autowired NegotiationRepository negotiations;
    @Autowired NegotiationHistoryRepository history;
    @Autowired ReviewRepository reviews;
    @Autowired FavoriteRepository favorites;
    @Autowired SearchAlertRepository alerts;
    @Autowired jakarta.persistence.EntityManager em;

    private User vendeur;
    private User acheteur;
    private Product annonce;
    private Negotiation offre;

    @BeforeEach
    void setUp() {
        vendeur = users.save(User.builder().username("del-vendeur").phone(44110001L).pwd("x").role(Role.USER).build());
        acheteur = users.save(User.builder().username("del-acheteur").phone(44110002L).pwd("x").role(Role.USER).build());
        annonce = products.save(Product.builder().title("Carton").user(vendeur)
                .status(ProductStatus.AVAILABLE).price(10.0).quantityTotal(10L).quantityAvailable(10L)
                .createdAt(OffsetDateTime.now()).build());
        offre = negotiations.save(Negotiation.builder().sender(acheteur).receiver(vendeur).product(annonce)
                .status("accepted").price(9.0).quantity(2).createdAt(OffsetDateTime.now()).build());
        history.save(NegotiationHistory.builder().negotiation(offre).author(acheteur).kind("OFFER")
                .price(9.0).quantity(2).createdAt(OffsetDateTime.now()).build());
        reviews.save(Review.builder().negotiation(offre).author(acheteur).target(vendeur).rating(5)
                .comment("parfait").createdAt(OffsetDateTime.now()).build());
        favorites.save(Favorite.builder().user(acheteur).product(annonce).createdAt(OffsetDateTime.now()).build());
        alerts.save(SearchAlert.builder().user(vendeur).keyword("carton").active(true)
                .createdAt(OffsetDateTime.now()).build());
        em.flush();
    }

    @Test
    @DisplayName("supprimer le vendeur emporte annonce, offre, fil, avis, favoris d'autrui et veilles")
    void supprimerLeVendeur() {
        assertDoesNotThrow(() -> accountDeletion.deleteAccount(vendeur.getId()));
        em.flush();
        em.clear();

        assertTrue(users.findById(vendeur.getId()).isEmpty());
        assertTrue(products.findById(annonce.getId()).isEmpty());
        assertTrue(negotiations.findById(offre.getId()).isEmpty());
        assertTrue(history.findByNegotiationIdOrderByCreatedAtAsc(offre.getId()).isEmpty());
        assertTrue(reviews.findByTargetIdOrderByCreatedAtDesc(vendeur.getId()).isEmpty());
        assertTrue(favorites.findByUserIdOrderByCreatedAtDesc(acheteur.getId()).isEmpty(),
                "le favori de l'acheteur pointait vers l'annonce supprimee");
        assertTrue(alerts.findByUserIdOrderByCreatedAtDesc(vendeur.getId()).isEmpty());
        assertTrue(users.findById(acheteur.getId()).isPresent(), "l'acheteur n'est pas concerne");
    }

    @Test
    @DisplayName("supprimer l'acheteur emporte son offre, son avis et son favori, pas l'annonce")
    void supprimerLAcheteur() {
        assertDoesNotThrow(() -> accountDeletion.deleteAccount(acheteur.getId()));
        em.flush();
        em.clear();

        assertTrue(users.findById(acheteur.getId()).isEmpty());
        assertTrue(negotiations.findById(offre.getId()).isEmpty());
        assertTrue(history.findByNegotiationIdOrderByCreatedAtAsc(offre.getId()).isEmpty());
        assertTrue(reviews.findByNegotiationId(offre.getId()).isEmpty());
        assertTrue(products.findById(annonce.getId()).isPresent(), "l'annonce du vendeur reste");
    }

    @Test
    @DisplayName("supprimer une annonce mise en favori et negociee ne rompt aucune contrainte")
    void supprimerUneAnnonce() {
        assertTrue(productDeletion.delete(annonce.getId()));
        em.flush();
        em.clear();

        assertTrue(products.findById(annonce.getId()).isEmpty());
        assertTrue(negotiations.findById(offre.getId()).isEmpty());
        assertTrue(favorites.findByUserIdOrderByCreatedAtDesc(acheteur.getId()).isEmpty());
        assertTrue(users.findById(vendeur.getId()).isPresent());
    }
}
