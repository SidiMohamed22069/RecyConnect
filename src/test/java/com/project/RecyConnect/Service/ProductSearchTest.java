package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.ProductDTO;
import com.project.RecyConnect.DTO.ProductSearchCriteria;
import com.project.RecyConnect.Model.Moughataa;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.ProductStatus;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.CategoryRepository;
import com.project.RecyConnect.Repository.FavoriteRepository;
import com.project.RecyConnect.Repository.NegotiationRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * La recherche d'annonces: son ordre, ses filtres et sa pagination.
 *
 * <p>Ce qui est verrouille ici, c'est surtout la <em>stabilite</em> de l'ordre.
 * Les resultats etaient melanges au hasard a chaque chargement: l'annonce vue
 * la veille etait introuvable le lendemain, et deux pages tirees de deux
 * melanges differents se recouvraient — la pagination n'avait aucun sens.
 */
@ExtendWith(MockitoExtension.class)
class ProductSearchTest {

    @Mock private ProductRepository repo;
    @Mock private CategoryRepository categoryRepo;
    @Mock private UserRepo userRepo;
    @Mock private NegotiationService negotiationService;
    @Mock private FileUrlService fileUrlService;
    @Mock private NegotiationRepository negotiationRepo;
    @Mock private FavoriteRepository favoriteRepo;
    @Mock private SearchAlertService searchAlertService;

    @InjectMocks private ProductService service;

    private User vendeur;

    @BeforeEach
    void setUp() {
        vendeur = User.builder().id(1L).username("Ahmed").build();
        lenient().when(fileUrlService.toPublicUrls(anyList())).thenReturn(List.of());
    }

    private Product annonce(long id, String titre, double prix, long quantite,
                            OffsetDateTime date, Moughataa lieu) {
        return Product.builder()
                .id(id)
                .title(titre)
                .description("")
                .price(prix)
                .unit("kg")
                .quantityTotal(quantite)
                .quantityAvailable(quantite)
                .status(ProductStatus.AVAILABLE)
                .location(lieu)
                .createdAt(date)
                .user(vendeur)
                .build();
    }

    private List<Long> idsOf(List<ProductDTO> rows) {
        return rows.stream().map(ProductDTO::getId).toList();
    }

    @Test
    @DisplayName("Sans tri demande, les annonces les plus recentes viennent en tete")
    void defaultSortIsNewestFirst() {
        when(repo.findAll()).thenReturn(List.of(
                annonce(1L, "Vieux lot", 10.0, 100L, OffsetDateTime.parse("2026-01-01T10:00:00Z"), null),
                annonce(2L, "Lot recent", 30.0, 50L, OffsetDateTime.parse("2026-08-01T10:00:00Z"), null),
                annonce(3L, "Lot moyen", 20.0, 70L, OffsetDateTime.parse("2026-05-01T10:00:00Z"), null)
        ));

        List<ProductDTO> rows = service.search(ProductSearchCriteria.builder().build(), null);

        assertEquals(List.of(2L, 3L, 1L), idsOf(rows));
    }

    @Test
    @DisplayName("Deux recherches identiques rendent le meme ordre")
    void orderIsStableAcrossCalls() {
        List<Product> catalogue = List.of(
                annonce(1L, "A", 10.0, 100L, OffsetDateTime.parse("2026-01-01T10:00:00Z"), null),
                annonce(2L, "B", 30.0, 50L, OffsetDateTime.parse("2026-08-01T10:00:00Z"), null),
                annonce(3L, "C", 20.0, 70L, OffsetDateTime.parse("2026-05-01T10:00:00Z"), null)
        );
        when(repo.findAll()).thenReturn(catalogue);

        List<Long> premiere = idsOf(service.search(ProductSearchCriteria.builder().build(), null));
        List<Long> seconde = idsOf(service.search(ProductSearchCriteria.builder().build(), null));

        assertEquals(premiere, seconde);
    }

    @Test
    @DisplayName("Deux annonces publiees a la meme seconde gardent un ordre determine")
    void tiesAreBrokenByIdSoPaginationNeverSkipsARow() {
        OffsetDateTime memeInstant = OffsetDateTime.parse("2026-08-01T10:00:00Z");
        when(repo.findAll()).thenReturn(List.of(
                annonce(1L, "A", 10.0, 100L, memeInstant, null),
                annonce(2L, "B", 30.0, 50L, memeInstant, null)
        ));

        assertEquals(List.of(2L, 1L),
                idsOf(service.search(ProductSearchCriteria.builder().build(), null)));
    }

    @Test
    @DisplayName("Le tri par prix croissant met le moins cher en tete")
    void sortByPriceAscending() {
        when(repo.findAll()).thenReturn(List.of(
                annonce(1L, "A", 30.0, 100L, OffsetDateTime.parse("2026-01-01T10:00:00Z"), null),
                annonce(2L, "B", 10.0, 50L, OffsetDateTime.parse("2026-08-01T10:00:00Z"), null)
        ));

        List<ProductDTO> rows = service.search(
                ProductSearchCriteria.builder().sort("price_asc").build(), null);

        assertEquals(List.of(2L, 1L), idsOf(rows));
    }

    @Test
    @DisplayName("La fourchette de prix ecarte ce qui sort des bornes")
    void priceRangeFilters() {
        when(repo.findAll()).thenReturn(List.of(
                annonce(1L, "A", 5.0, 100L, OffsetDateTime.parse("2026-01-01T10:00:00Z"), null),
                annonce(2L, "B", 25.0, 100L, OffsetDateTime.parse("2026-01-01T10:00:00Z"), null),
                annonce(3L, "C", 90.0, 100L, OffsetDateTime.parse("2026-01-01T10:00:00Z"), null)
        ));

        List<ProductDTO> rows = service.search(ProductSearchCriteria.builder()
                .minPrice(10.0).maxPrice(50.0).build(), null);

        assertEquals(List.of(2L), idsOf(rows));
    }

    @Test
    @DisplayName("Le filtre de zone ne rend que la moughataa demandee")
    void locationFilters() {
        when(repo.findAll()).thenReturn(List.of(
                annonce(1L, "A", 10.0, 100L, OffsetDateTime.parse("2026-01-01T10:00:00Z"), Moughataa.KSAR),
                annonce(2L, "B", 10.0, 100L, OffsetDateTime.parse("2026-01-01T10:00:00Z"), Moughataa.ARAFAT)
        ));

        List<ProductDTO> rows = service.search(ProductSearchCriteria.builder()
                .location(Moughataa.ARAFAT).build(), null);

        assertEquals(List.of(2L), idsOf(rows));
    }

    @Test
    @DisplayName("La recherche textuelle regarde aussi la description")
    void textSearchLooksAtDescription() {
        Product cables = annonce(1L, "Lot de cables", 10.0, 100L,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"), null);
        cables.setDescription("Cuivre denude, 40 kg");
        when(repo.findAll()).thenReturn(List.of(
                cables,
                annonce(2L, "Bouteilles PET", 10.0, 100L,
                        OffsetDateTime.parse("2026-01-01T10:00:00Z"), null)
        ));

        List<ProductDTO> rows = service.search(
                ProductSearchCriteria.builder().query("cuivre").build(), null);

        assertEquals(List.of(1L), idsOf(rows));
    }

    @Test
    @DisplayName("Les pages successives ne se recouvrent pas et n'oublient rien")
    void pagesCoverTheCatalogueExactlyOnce() {
        when(repo.findAll()).thenReturn(List.of(
                annonce(1L, "A", 10.0, 10L, OffsetDateTime.parse("2026-01-01T10:00:00Z"), null),
                annonce(2L, "B", 10.0, 10L, OffsetDateTime.parse("2026-02-01T10:00:00Z"), null),
                annonce(3L, "C", 10.0, 10L, OffsetDateTime.parse("2026-03-01T10:00:00Z"), null),
                annonce(4L, "D", 10.0, 10L, OffsetDateTime.parse("2026-04-01T10:00:00Z"), null),
                annonce(5L, "E", 10.0, 10L, OffsetDateTime.parse("2026-05-01T10:00:00Z"), null)
        ));

        List<Long> page0 = idsOf(service.search(
                ProductSearchCriteria.builder().page(0).size(2).build(), null));
        List<Long> page1 = idsOf(service.search(
                ProductSearchCriteria.builder().page(1).size(2).build(), null));
        List<Long> page2 = idsOf(service.search(
                ProductSearchCriteria.builder().page(2).size(2).build(), null));

        assertEquals(List.of(5L, 4L), page0);
        assertEquals(List.of(3L, 2L), page1);
        assertEquals(List.of(1L), page2);
    }

    @Test
    @DisplayName("Une page au-dela de la fin rend une liste vide, pas une erreur")
    void pageBeyondEndIsEmpty() {
        when(repo.findAll()).thenReturn(List.of(
                annonce(1L, "A", 10.0, 10L, OffsetDateTime.parse("2026-01-01T10:00:00Z"), null)
        ));

        assertTrue(service.search(
                ProductSearchCriteria.builder().page(9).size(20).build(), null).isEmpty());
    }

    @Test
    @DisplayName("Sans taille de page, le catalogue part entier")
    void noSizeMeansNoPagination() {
        when(repo.findAll()).thenReturn(List.of(
                annonce(1L, "A", 10.0, 10L, OffsetDateTime.parse("2026-01-01T10:00:00Z"), null),
                annonce(2L, "B", 10.0, 10L, OffsetDateTime.parse("2026-02-01T10:00:00Z"), null)
        ));

        assertEquals(2, service.search(ProductSearchCriteria.builder().build(), null).size());
    }

    @Test
    @DisplayName("Les annonces en pause ne paraissent plus au catalogue")
    void pausedListingsLeaveTheCatalogue() {
        Product enPause = annonce(2L, "En pause", 10.0, 10L,
                OffsetDateTime.parse("2026-02-01T10:00:00Z"), null);
        enPause.setStatus(ProductStatus.PAUSED);
        when(repo.findAll()).thenReturn(List.of(
                annonce(1L, "Disponible", 10.0, 10L,
                        OffsetDateTime.parse("2026-01-01T10:00:00Z"), null),
                enPause
        ));

        assertEquals(List.of(1L),
                idsOf(service.search(ProductSearchCriteria.builder().build(), null)));
    }
}
