package com.project.RecyConnect.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.project.RecyConnect.DTO.ProductDTO;
import com.project.RecyConnect.DTO.ProductSearchCriteria;
import com.project.RecyConnect.Model.Category;
import com.project.RecyConnect.Model.GeoPrecision;
import com.project.RecyConnect.Model.Moughataa;
import com.project.RecyConnect.Model.Negotiation;
import com.project.RecyConnect.Model.NegotiationStatus;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.ProductStatus;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.CategoryRepository;
import com.project.RecyConnect.Repository.NegotiationRepository;
import com.project.RecyConnect.Repository.NotificationRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.UserRepo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La position d'une annonce: ce qu'on en stocke, ce qu'on en montre, et a qui.
 *
 * <p>Deux promesses sont verifiees ici. La premiere est fonctionnelle: une
 * annonce publiee doit etre visible — elle ne l'etait pas. La seconde tient a
 * la vie privee: le lot est souvent chez un particulier, et publier son point
 * exact revient a publier son adresse. Une application peut bien arrondir ce
 * qu'elle dessine; si le serveur a envoye le point precis, il a deja quitte la
 * maison.
 */
@SpringBootTest
class ProductGeoServiceTest {

    @Autowired private ProductService productService;
    @Autowired private ProductRepository products;
    @Autowired private UserRepo users;
    @Autowired private CategoryRepository categories;
    @Autowired private NegotiationRepository negotiations;
    @Autowired private NotificationRepository notifications;

    /** Une cour de Tevragh Zeina, au metre pres. */
    private static final double LAT = 18.09765;
    private static final double LNG = -15.98432;

    private User vendeur;
    private User acheteur;
    private Category categorie;

    @BeforeEach
    void seed() {
        vendeur = users.save(User.builder().username("Vendeur Geo").phone(22277001L)
                .pwd("hash").role(Role.USER).build());
        acheteur = users.save(User.builder().username("Acheteur Geo").phone(22277002L)
                .pwd("hash").role(Role.USER).build());
        categorie = categories.save(Category.builder().name("Plastique Geo").build());
    }

    @AfterEach
    void cleanUp() {
        negotiations.deleteAll(negotiations.findBySenderId(acheteur.getId()));
        products.deleteAll(products.findByUserId(vendeur.getId()));
        categories.deleteById(categorie.getId());
        for (User u : List.of(vendeur, acheteur)) {
            notifications.deleteAll(notifications.findByReceiverId(u.getId()));
            notifications.deleteAll(notifications.findBySenderId(u.getId()));
            users.deleteById(u.getId());
        }
    }

    private Product save(Double lat, Double lng, GeoPrecision precision, Moughataa zone) {
        return products.save(Product.builder()
                .title("Bouteilles PET")
                .description("Lot trie")
                .price(25.0)
                .unit("KG")
                .quantityTotal(100L)
                .quantityAvailable(100L)
                .status(ProductStatus.AVAILABLE)
                .createdAt(OffsetDateTime.now())
                .location(zone)
                .latitude(lat)
                .longitude(lng)
                .geoPrecision(precision)
                .category(categorie)
                .user(vendeur)
                .build());
    }

    // ------------------------------------------------- une annonce visible

    @Test
    @DisplayName("une annonce publiee en \"pending\" devient disponible")
    void statutDePublicationNormalise() {
        // C'est ce que l'application mobile envoyait. La recherche ne rend que
        // les annonces "available", et rien nulle part ne faisait passer une
        // annonce de l'un a l'autre: toute annonce publiee depuis le telephone
        // etait enregistree, puis introuvable — y compris par son auteur.
        ProductDTO dto = new ProductDTO();
        dto.setTitle("Cuivre");
        dto.setDesc("Lot de cables");
        dto.setPrice(300.0);
        dto.setUnit("KG");
        dto.setQuantityTotal(50L);
        dto.setQuantityAvailable(50L);
        dto.setStatus(ProductStatus.PENDING);
        dto.setCategoryId(categorie.getId());
        dto.setUserId(vendeur.getId());

        ProductDTO cree = productService.save(dto);
        assertEquals(ProductStatus.AVAILABLE, cree.getStatus());

        List<ProductDTO> trouvees = productService.search(
                ProductSearchCriteria.builder().query("Cuivre").build(), null);
        assertTrue(trouvees.stream().anyMatch(p -> p.getId().equals(cree.getId())),
                "l'annonce publiee doit apparaitre dans la recherche");
    }

    @Test
    @DisplayName("un statut choisi par le vendeur est respecte")
    void statutChoisiRespecte() {
        // La normalisation ne doit pas confisquer les statuts qui, eux, se
        // demandent legitimement.
        ProductDTO dto = new ProductDTO();
        dto.setTitle("Verre en pause");
        dto.setStatus(ProductStatus.PAUSED);
        dto.setCategoryId(categorie.getId());
        dto.setUserId(vendeur.getId());

        assertEquals(ProductStatus.PAUSED, productService.save(dto).getStatus());
    }

    // ------------------------------------------------------- la vie privee

    @Test
    @DisplayName("un tiers ne recoit qu'une position arrondie")
    void positionArrondiePourUnTiers() {
        Product annonce = save(LAT, LNG, GeoPrecision.APPROX, Moughataa.TEVRAGH_ZEINA);

        ProductDTO vue = productService.findById(annonce.getId(), acheteur.getId()).orElseThrow();

        assertNotNull(vue.getLatitude());
        assertNotEquals(LAT, vue.getLatitude(), "le point exact ne doit pas sortir");
        assertNotEquals(LNG, vue.getLongitude(), "le point exact ne doit pas sortir");
        // Assez proche pour situer la rue, trop loin pour designer la maison.
        assertTrue(GeoSupport.distanceKm(LAT, LNG, vue.getLatitude(), vue.getLongitude()) < 0.25);
        assertEquals(GeoPrecision.APPROX, vue.getGeoPrecision());
    }

    @Test
    @DisplayName("un visiteur sans compte n'a pas droit a mieux")
    void positionArrondiePourUnAnonyme() {
        Product annonce = save(LAT, LNG, GeoPrecision.APPROX, Moughataa.TEVRAGH_ZEINA);

        ProductDTO vue = productService.findById(annonce.getId(), null).orElseThrow();
        assertNotEquals(LAT, vue.getLatitude());
    }

    @Test
    @DisplayName("le vendeur revoit son propre point, au metre pres")
    void positionExactePourLAuteur() {
        // Il doit pouvoir verifier — et corriger — le point qu'il a pose.
        Product annonce = save(LAT, LNG, GeoPrecision.APPROX, Moughataa.TEVRAGH_ZEINA);

        ProductDTO vue = productService.findById(annonce.getId(), vendeur.getId()).orElseThrow();
        assertEquals(LAT, vue.getLatitude());
        assertEquals(LNG, vue.getLongitude());
    }

    @Test
    @DisplayName("un vendeur qui publie son point exact le publie pour tous")
    void precisionExacteRespectee() {
        // Une cour, un atelier, un entrepot: le vendeur veut etre trouve.
        Product annonce = save(LAT, LNG, GeoPrecision.EXACT, Moughataa.TEVRAGH_ZEINA);

        ProductDTO vue = productService.findById(annonce.getId(), acheteur.getId()).orElseThrow();
        assertEquals(LAT, vue.getLatitude());
    }

    @Test
    @DisplayName("l'acheteur dont l'offre est acceptee obtient le point exact")
    void positionExactePourLAcheteurAccepte() {
        Product annonce = save(LAT, LNG, GeoPrecision.APPROX, Moughataa.TEVRAGH_ZEINA);

        // Avant l'acceptation, rien de plus qu'un quartier.
        assertNotEquals(LAT,
                productService.findById(annonce.getId(), acheteur.getId()).orElseThrow().getLatitude());

        negotiations.save(Negotiation.builder()
                .sender(acheteur).receiver(vendeur).product(annonce)
                .status(NegotiationStatus.STATUS_ACCEPTED)
                .price(25.0).quantity(10)
                .createdAt(OffsetDateTime.now())
                .build());

        // Apres, il doit pouvoir venir charger: c'est le moment ou les deux ont
        // deja echange leurs numeros.
        assertEquals(LAT,
                productService.findById(annonce.getId(), acheteur.getId()).orElseThrow().getLatitude());
    }

    @Test
    @DisplayName("une offre en attente n'ouvre pas encore l'adresse")
    void offreEnAttenteNOuvreRien() {
        Product annonce = save(LAT, LNG, GeoPrecision.APPROX, Moughataa.TEVRAGH_ZEINA);
        negotiations.save(Negotiation.builder()
                .sender(acheteur).receiver(vendeur).product(annonce)
                .status(NegotiationStatus.STATUS_PENDING)
                .price(20.0).quantity(5)
                .createdAt(OffsetDateTime.now())
                .build());

        assertNotEquals(LAT,
                productService.findById(annonce.getId(), acheteur.getId()).orElseThrow().getLatitude());
    }

    @Test
    @DisplayName("la liste de recherche n'expose jamais le point exact")
    void rechercheNExposePas() {
        Product annonce = save(LAT, LNG, GeoPrecision.APPROX, Moughataa.TEVRAGH_ZEINA);

        ProductDTO vue = productService.search(
                        ProductSearchCriteria.builder().query("Bouteilles PET").build(),
                        acheteur.getId())
                .stream().filter(p -> p.getId().equals(annonce.getId())).findFirst().orElseThrow();

        assertNotEquals(LAT, vue.getLatitude());
    }

    @Test
    @DisplayName("la reponse a une modification suit la meme regle")
    void modificationNExposePasNonPlus() {
        Product annonce = save(LAT, LNG, GeoPrecision.APPROX, Moughataa.TEVRAGH_ZEINA);

        ProductDTO patch = new ProductDTO();
        patch.setTitle("Bouteilles PET triees");

        // Le vendeur revoit son point.
        assertEquals(LAT,
                productService.patch(annonce.getId(), patch, vendeur.getId()).getLatitude());

        // Un administrateur qui corrige l'annonce d'autrui, non : rien dans
        // "corriger un titre" ne demande de connaitre l'adresse d'un
        // particulier.
        assertNotEquals(LAT,
                productService.patch(annonce.getId(), patch, acheteur.getId()).getLatitude());
    }

    // ------------------------------------------------------------ la carte

    @Test
    @DisplayName("la carte rend les annonces du rectangle")
    void carteRendLeRectangle() {
        Product dedans = save(LAT, LNG, GeoPrecision.EXACT, Moughataa.TEVRAGH_ZEINA);
        // Rosso, a deux cents kilometres au sud.
        Product dehors = save(16.5138, -15.8050, GeoPrecision.EXACT, Moughataa.AUTRE);

        List<Long> ids = productService.mapArea(18.05, 18.15, -16.05, -15.90,
                        null, null, null, Set.of(), null)
                .stream().map(ProductDTO::getId).toList();

        assertTrue(ids.contains(dedans.getId()));
        assertFalse(ids.contains(dehors.getId()), "une annonce hors cadre n'a rien a y faire");
    }

    @Test
    @DisplayName("une annonce sans point compte quand meme, par sa moughataa")
    void carteRetombeSurLeQuartier() {
        // Aucune annonce publiee avant la carte ne porte de coordonnees:
        // sans ce repli, la carte serait restee vide des semaines.
        Product sansPoint = save(null, null, null, Moughataa.TEVRAGH_ZEINA);

        List<ProductDTO> vues = productService.mapArea(18.05, 18.15, -16.05, -15.90,
                null, null, null, Set.of(), null);

        assertTrue(vues.stream().anyMatch(p -> p.getId().equals(sansPoint.getId())));
        // Elle ne prend pas pour autant de fausses coordonnees: c'est au client
        // de la placer au centre du quartier, et de le dire.
        ProductDTO vue = vues.stream().filter(p -> p.getId().equals(sansPoint.getId()))
                .findFirst().orElseThrow();
        assertNull(vue.getLatitude());
        assertEquals(Moughataa.TEVRAGH_ZEINA, vue.getLocation());
    }

    @Test
    @DisplayName("un rectangle donne a l'envers reste un rectangle")
    void carteToleresLesBornesInversees() {
        Product annonce = save(LAT, LNG, GeoPrecision.EXACT, Moughataa.TEVRAGH_ZEINA);

        List<Long> ids = productService.mapArea(18.15, 18.05, -15.90, -16.05,
                        null, null, null, Set.of(), null)
                .stream().map(ProductDTO::getId).toList();

        assertTrue(ids.contains(annonce.getId()));
    }

    // -------------------------------------------------------- la proximite

    @Test
    @DisplayName("les plus proches d'abord, et hors du rayon: personne")
    void proximiteOrdonneEtBorne() {
        Product proche = save(LAT, LNG, GeoPrecision.EXACT, Moughataa.TEVRAGH_ZEINA);
        // Riyad, a l'autre bout de Nouakchott.
        Product loin = save(18.0106, -15.8878, GeoPrecision.EXACT, Moughataa.RIYAD);

        List<ProductDTO> autour = productService.nearby(LAT, LNG, 30.0, null,
                null, Set.of(), null);
        List<Long> ids = autour.stream().map(ProductDTO::getId).toList();
        assertTrue(ids.indexOf(proche.getId()) < ids.indexOf(loin.getId()));

        List<Long> serres = productService.nearby(LAT, LNG, 2.0, null, null, Set.of(), null)
                .stream().map(ProductDTO::getId).toList();
        assertTrue(serres.contains(proche.getId()));
        assertFalse(serres.contains(loin.getId()));
    }

    @Test
    @DisplayName("la distance n'est annoncee que si l'annonce a un vrai point")
    void distanceSeulementSurUnVraiPoint() {
        Product situee = save(LAT, LNG, GeoPrecision.EXACT, Moughataa.TEVRAGH_ZEINA);
        Product quartierSeul = save(null, null, null, Moughataa.TEVRAGH_ZEINA);

        List<ProductDTO> autour = productService.nearby(LAT, LNG, 30.0, null,
                null, Set.of(), null);

        ProductDTO a = autour.stream().filter(p -> p.getId().equals(situee.getId()))
                .findFirst().orElseThrow();
        ProductDTO b = autour.stream().filter(p -> p.getId().equals(quartierSeul.getId()))
                .findFirst().orElseThrow();

        assertNotNull(a.getDistanceKm());
        // Annoncer "2,3 km" a partir du centre d'un quartier donnerait une
        // precision que personne n'a mesuree.
        assertNull(b.getDistanceKm());
    }

    @Test
    @DisplayName("un rayon sans centre ne filtre rien")
    void rayonSansCentreIgnore() {
        Product annonce = save(LAT, LNG, GeoPrecision.EXACT, Moughataa.TEVRAGH_ZEINA);

        List<Long> ids = productService.search(ProductSearchCriteria.builder()
                        .maxDistanceKm(1.0)
                        .build(), null)
                .stream().map(ProductDTO::getId).toList();

        assertTrue(ids.contains(annonce.getId()),
                "un rayon sans centre doit etre ignore, pas vider le catalogue");
    }

    @Test
    @DisplayName("la recherche par rayon accepte le quartier comme repli")
    void rechercheParRayonAvecRepli() {
        Product quartierSeul = save(null, null, null, Moughataa.TEVRAGH_ZEINA);

        List<Long> ids = productService.search(ProductSearchCriteria.builder()
                        .centerLatitude(LAT)
                        .centerLongitude(LNG)
                        .maxDistanceKm(5.0)
                        .build(), null)
                .stream().map(ProductDTO::getId).toList();

        // Sans ce repli, un rayon ferait disparaitre presque tout le catalogue.
        assertTrue(ids.contains(quartierSeul.getId()));
    }
}
