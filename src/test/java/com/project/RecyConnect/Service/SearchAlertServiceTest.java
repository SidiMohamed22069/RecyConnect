package com.project.RecyConnect.Service;

import com.project.RecyConnect.Model.Category;
import com.project.RecyConnect.Model.Moughataa;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.ProductStatus;
import com.project.RecyConnect.Model.SearchAlert;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.CategoryRepository;
import com.project.RecyConnect.Repository.SearchAlertRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le declenchement des veilles de recherche.
 *
 * <p>Une alerte de trop est du bruit, et le bruit fait couper les
 * notifications: ces tests verrouillent surtout ce qui ne doit <em>pas</em>
 * partir.
 */
@ExtendWith(MockitoExtension.class)
class SearchAlertServiceTest {

    @Mock private SearchAlertRepository repo;
    @Mock private UserRepo userRepo;
    @Mock private CategoryRepository categoryRepo;
    @Mock private NotificationService notificationService;

    @InjectMocks private SearchAlertService service;

    private User veilleur;
    private User vendeur;
    private Category metaux;

    @BeforeEach
    void setUp() {
        veilleur = User.builder().id(1L).username("Ahmed").build();
        vendeur = User.builder().id(2L).username("Fatima").build();
        metaux = new Category();
        metaux.setId(5L);
        metaux.setName("Metaux");
    }

    private Product annonce(String titre, double prix, long quantite, Moughataa lieu, Category categorie) {
        return Product.builder()
                .id(10L)
                .title(titre)
                .description("")
                .price(prix)
                .quantityAvailable(quantite)
                .status(ProductStatus.AVAILABLE)
                .location(lieu)
                .category(categorie)
                .user(vendeur)
                .build();
    }

    private SearchAlert veille(String motCle, Double prixMax, Long quantiteMin,
                               Moughataa lieu, Category categorie) {
        return SearchAlert.builder()
                .id(1L)
                .user(veilleur)
                .keyword(motCle)
                .maxPrice(prixMax)
                .minQuantity(quantiteMin)
                .location(lieu)
                .category(categorie)
                .active(true)
                .build();
    }

    private void verifyNotified(int fois) {
        verify(notificationService, times(fois)).sendLocalizedNotification(
                eq(1L), any(), any(), eq("SEARCH_ALERT"), any());
    }

    @Test
    @DisplayName("Une annonce qui satisfait tous les criteres declenche la veille")
    void matchingProductNotifies() {
        when(repo.findByActiveTrue()).thenReturn(List.of(
                veille("cuivre", 300.0, 20L, Moughataa.KSAR, metaux)));

        service.notifyMatching(annonce("Cuivre denude", 250.0, 40L, Moughataa.KSAR, metaux));

        verifyNotified(1);
    }

    @Test
    @DisplayName("Un prix au-dessus du plafond ne declenche rien")
    void priceAboveCeilingIsSilent() {
        when(repo.findByActiveTrue()).thenReturn(List.of(
                veille("cuivre", 300.0, null, null, null)));

        service.notifyMatching(annonce("Cuivre denude", 450.0, 40L, null, null));

        verifyNotified(0);
    }

    @Test
    @DisplayName("Une quantite inferieure au minimum ne declenche rien")
    void quantityBelowFloorIsSilent() {
        when(repo.findByActiveTrue()).thenReturn(List.of(
                veille(null, null, 100L, null, null)));

        service.notifyMatching(annonce("Cuivre denude", 250.0, 40L, null, null));

        verifyNotified(0);
    }

    @Test
    @DisplayName("Une autre moughataa ne declenche rien")
    void otherLocationIsSilent() {
        when(repo.findByActiveTrue()).thenReturn(List.of(
                veille(null, null, null, Moughataa.ARAFAT, null)));

        service.notifyMatching(annonce("Cuivre denude", 250.0, 40L, Moughataa.KSAR, null));

        verifyNotified(0);
    }

    @Test
    @DisplayName("On ne previent pas un vendeur de sa propre publication")
    void ownerIsNeverNotifiedOfTheirOwnListing() {
        SearchAlert sienne = veille(null, null, null, null, null);
        sienne.setUser(vendeur);
        when(repo.findByActiveTrue()).thenReturn(List.of(sienne));

        service.notifyMatching(annonce("Cuivre denude", 250.0, 40L, null, null));

        verify(notificationService, never()).sendLocalizedNotification(
                any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Une annonce qui n'est pas au catalogue ne declenche aucune veille")
    void nonAvailableProductIsSilent() {
        Product enPause = annonce("Cuivre denude", 250.0, 40L, null, null);
        enPause.setStatus(ProductStatus.PAUSED);

        service.notifyMatching(enPause);

        verify(repo, never()).findByActiveTrue();
        verify(notificationService, never()).sendLocalizedNotification(
                any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Une notification en echec ne fait pas echouer la publication")
    void notificationFailureIsSwallowed() {
        when(repo.findByActiveTrue()).thenReturn(List.of(veille(null, null, null, null, null)));
        org.mockito.Mockito.doThrow(new RuntimeException("FCM indisponible"))
                .when(notificationService).sendLocalizedNotification(
                        any(), any(), any(), any(), any());

        service.notifyMatching(annonce("Cuivre denude", 250.0, 40L, null, null));
    }
}
