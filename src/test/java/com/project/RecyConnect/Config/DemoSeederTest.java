package com.project.RecyConnect.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.project.RecyConnect.Model.Category;
import com.project.RecyConnect.Model.GeoPrecision;
import com.project.RecyConnect.Model.Moughataa;
import com.project.RecyConnect.Model.Negotiation;
import com.project.RecyConnect.Model.NegotiationHistory;
import com.project.RecyConnect.Model.NegotiationStatus;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.SupportedLanguage;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.CategoryRepository;
import com.project.RecyConnect.Repository.NegotiationHistoryRepository;
import com.project.RecyConnect.Repository.NegotiationRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.UserRepo;
import com.project.RecyConnect.Service.GeoSupport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verrouille le jeu de demonstration: desactive par defaut, mots de passe
 * haches, aucune duplication au redemarrage, et rien qui s'accroche a un compte
 * reel.
 *
 * <p>Verrouille aussi les listes que le seeder porte, qui se relisent mal: une
 * offre posee par le proprietaire de l'annonce, une quantite vendue superieure
 * au stock ou une annonce sans coordonnees ne se voient qu'en ouvrant
 * l'application, souvent pendant la demonstration elle-meme.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DemoSeederTest {

    private static final String PASSWORD = "demo1234";

    /** Le rayon au-dela duquel un point n'est plus dans Nouakchott. */
    private static final double NOUAKCHOTT_RADIUS_KM = 25.0;

    @Mock private UserRepo userRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private NegotiationRepository negotiationRepository;
    @Mock private NegotiationHistoryRepository negotiationHistoryRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Une base simulee, et non des mocks sans memoire: le seeder relit ce qu'il
     * vient d'ecrire pour situer les annonces et leur poser des offres.
     */
    private final List<User> existingUsers = new ArrayList<>();
    private final List<User> savedUsers = new ArrayList<>();
    private final List<Product> savedProducts = new ArrayList<>();
    private final List<Negotiation> savedOffers = new ArrayList<>();
    private final List<NegotiationHistory> savedHistory = new ArrayList<>();

    /** Le catalogue tel que l'aurait laisse {@link CategorySeeder}. */
    private static List<Category> catalogue() {
        List<Category> categories = new ArrayList<>();
        long id = 1;
        for (CategorySeeder.Seed seed : CategorySeeder.CATALOGUE) {
            categories.add(Category.builder()
                    .id(id++)
                    .code(seed.code())
                    .name(seed.nameEn())
                    .nameEn(seed.nameEn())
                    .nameFr(seed.nameFr())
                    .nameAr(seed.nameAr())
                    .build());
        }
        return categories;
    }

    @BeforeEach
    void setUp() {
        AtomicLong nextId = new AtomicLong(1);

        when(categoryRepository.findAll()).thenReturn(catalogue());

        when(userRepository.findByPhone(anyLong())).thenAnswer(call -> existingUsers.stream()
                .filter(user -> call.getArgument(0).equals(user.getPhone()))
                .findFirst().orElse(null));
        when(userRepository.findByUsername(anyString())).thenAnswer(call -> existingUsers.stream()
                .filter(user -> call.getArgument(0).equals(user.getUsername()))
                .findFirst().orElse(null));
        when(userRepository.save(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            user.setId(nextId.getAndIncrement());
            existingUsers.add(user);
            savedUsers.add(user);
            return user;
        });

        when(productRepository.findByUserId(anyLong())).thenAnswer(call -> {
            Long userId = call.getArgument(0);
            return savedProducts.stream()
                    .filter(product -> product.getUser() != null
                            && userId.equals(product.getUser().getId()))
                    .toList();
        });
        when(productRepository.save(any(Product.class))).thenAnswer(call -> {
            Product product = call.getArgument(0);
            if (product.getId() == null) {
                product.setId(nextId.getAndIncrement());
                savedProducts.add(product);
            }
            return product;
        });

        when(negotiationRepository.findByProductId(anyLong())).thenAnswer(call -> {
            Long productId = call.getArgument(0);
            return savedOffers.stream()
                    .filter(offer -> offer.getProduct() != null
                            && productId.equals(offer.getProduct().getId()))
                    .toList();
        });
        when(negotiationRepository.save(any(Negotiation.class))).thenAnswer(call -> {
            Negotiation offer = call.getArgument(0);
            offer.setId(nextId.getAndIncrement());
            savedOffers.add(offer);
            return offer;
        });
        when(negotiationHistoryRepository.save(any(NegotiationHistory.class))).thenAnswer(call -> {
            NegotiationHistory entry = call.getArgument(0);
            savedHistory.add(entry);
            return entry;
        });
    }

    private DemoSeeder seeder(boolean enabled, String password) {
        return new DemoSeeder(userRepository, categoryRepository, productRepository,
                negotiationRepository, negotiationHistoryRepository, passwordEncoder,
                enabled, password);
    }

    private void run() {
        seeder(true, PASSWORD).run(null);
    }

    // --- Garde-fous d'origine --------------------------------------------

    @Test
    @DisplayName("Cree quatre comptes et dix annonces sur une base vide")
    void seedsFourUsersAndTenProducts() {
        run();

        assertEquals(4, savedUsers.size());
        for (User user : savedUsers) {
            assertEquals(Role.USER, user.getRole());
            assertNotNull(user.getPhone());
            assertNotEquals(PASSWORD, user.getPwd(),
                    "Le mot de passe ne doit jamais etre stocke en clair");
            assertTrue(passwordEncoder.matches(PASSWORD, user.getPwd()));
        }

        assertEquals(10, savedProducts.size());
        for (Product product : savedProducts) {
            assertNotNull(product.getCategory(), "Chaque annonce est classee");
            assertNotNull(product.getUser(), "Chaque annonce a un vendeur");
            assertNotNull(product.getStatus());
            assertNotNull(product.getCreatedAt());
            assertTrue(product.getQuantityAvailable() <= product.getQuantityTotal(),
                    "La quantite disponible ne depasse jamais le total: " + product.getTitle());
            assertTrue(product.getImageUrls().isEmpty(),
                    "Le jeu de demonstration ne porte aucune photo");
        }
    }

    /**
     * Les unites doivent etre celles du formulaire mobile: une valeur libre
     * s'afficherait non traduite a cote des annonces reelles.
     */
    @Test
    @DisplayName("N'utilise que les unites connues du formulaire mobile")
    void usesOnlyKnownUnits() {
        run();

        for (Product product : savedProducts) {
            assertTrue(List.of("KG", "METRE", "UNIT").contains(product.getUnit()),
                    "Unite inconnue: " + product.getUnit());
        }
    }

    @Test
    @DisplayName("Couvre les cinq categories du catalogue")
    void spreadsProductsAcrossCatalogue() {
        run();

        List<String> codes = savedProducts.stream()
                .map(product -> product.getCategory().getCode())
                .distinct()
                .toList();

        assertEquals(5, codes.size(), "Une demonstration doit montrer toutes les categories");
    }

    @Test
    @DisplayName("Ne touche a rien quand le seed est desactive")
    void doesNothingWhenDisabled() {
        seeder(false, PASSWORD).run(null);

        verify(userRepository, never()).save(any(User.class));
        verify(productRepository, never()).save(any(Product.class));
        verify(negotiationRepository, never()).save(any(Negotiation.class));
    }

    @Test
    @DisplayName("Refuse de creer des comptes sans mot de passe configure")
    void doesNothingWithoutPassword() {
        seeder(true, "  ").run(null);

        verify(userRepository, never()).save(any(User.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    /**
     * Le seeder tourne a chaque demarrage: sans garde-fou, la liste grossirait
     * de dix annonces et de neuf offres a chaque relance.
     */
    @Test
    @DisplayName("Un redemarrage ne duplique ni comptes, ni annonces, ni offres")
    void isIdempotentAcrossRestarts() {
        run();
        int products = savedProducts.size();
        int offers = savedOffers.size();

        run();

        assertEquals(4, savedUsers.size(), "Aucun compte recree");
        assertEquals(products, savedProducts.size(), "Aucune annonce dupliquee");
        assertEquals(offers, savedOffers.size(), "Aucune offre dupliquee");
    }

    /**
     * Le numero est la cle de connexion. S'il appartient a quelqu'un d'autre,
     * lui accrocher des annonces fictives fausserait son profil.
     */
    @Test
    @DisplayName("Ignore un compte dont le numero appartient a un vrai utilisateur")
    void skipsUserWhosePhoneBelongsToSomeoneElse() {
        existingUsers.add(User.builder()
                .id(99L).username("Vrai Utilisateur").phone(36241590L).build());

        run();

        assertEquals(3, savedUsers.size());
        for (Product product : savedProducts) {
            assertNotEquals(99L, product.getUser().getId(),
                    "Aucune annonce de demonstration sur un compte reel");
        }
        for (Negotiation offer : savedOffers) {
            assertNotEquals(99L, offer.getSender().getId(),
                    "Aucune offre de demonstration au nom d'un compte reel");
            assertNotEquals(99L, offer.getReceiver().getId(),
                    "Aucune offre de demonstration adressee a un compte reel");
        }
    }

    @Test
    @DisplayName("N'invente aucune annonce quand le catalogue est vide")
    void skipsProductsWhenCatalogueMissing() {
        when(categoryRepository.findAll()).thenReturn(new ArrayList<>());

        run();

        assertEquals(4, savedUsers.size());
        verify(productRepository, never()).save(any(Product.class));
        verify(negotiationRepository, never()).save(any(Negotiation.class));
    }

    // --- Localisation ----------------------------------------------------

    @Test
    @DisplayName("toutes les annonces sont situees dans Nouakchott")
    void everyProductIsLocatedInNouakchott() {
        run();

        assertFalse(savedProducts.isEmpty(), "le seeder doit creer des annonces");
        for (Product product : savedProducts) {
            assertAll(product.getTitle(),
                    () -> assertNotNull(product.getLocation(), "moughataa manquante"),
                    () -> assertNotEquals(Moughataa.AUTRE, product.getLocation(),
                            "la zone 'autre' n'a pas de centre a afficher"),
                    () -> assertTrue(
                            GeoSupport.isValid(product.getLatitude(), product.getLongitude()),
                            "coordonnees manquantes ou invalides"),
                    () -> assertNotNull(product.getGeoPrecision(), "precision manquante"),
                    () -> assertTrue(
                            GeoSupport.distanceKm(product.getLatitude(), product.getLongitude(),
                                    GeoSupport.NOUAKCHOTT_LAT, GeoSupport.NOUAKCHOTT_LNG)
                                    < NOUAKCHOTT_RADIUS_KM,
                            "point hors de Nouakchott"));
        }
    }

    @Test
    @DisplayName("chaque annonce tombe dans la moughataa qu'elle declare")
    void everyPointSitsInItsDeclaredZone() {
        run();

        for (Product product : savedProducts) {
            Moughataa zone = product.getLocation();
            double distance = GeoSupport.distanceKm(
                    product.getLatitude(), product.getLongitude(),
                    zone.getCentroidLatitude(), zone.getCentroidLongitude());
            // Une moughataa de Nouakchott se traverse en quelques kilometres:
            // au-dela, le badge de lieu contredirait la carte.
            assertTrue(distance < 3.0,
                    product.getTitle() + ": " + Math.round(distance * 1000) + " m du centre de "
                            + zone.getValue());
        }
    }

    @Test
    @DisplayName("les deux niveaux de precision sont representes")
    void bothPrecisionsAreShown() {
        run();

        List<GeoPrecision> precisions = savedProducts.stream()
                .map(Product::getGeoPrecision).distinct().toList();
        assertTrue(precisions.contains(GeoPrecision.EXACT), "aucune annonce en precision exacte");
        assertTrue(precisions.contains(GeoPrecision.APPROX), "aucune annonce floutee");
    }

    /**
     * Le cas de la base de demonstration deja en service: ses annonces
     * existent, publiees avant la carte et sans offre. Le seeder ne les recree
     * pas — il doit donc les situer et les charger d'offres sur place, sans
     * quoi cette base garderait une carte vide et un onglet "Offres" vide.
     */
    @Test
    @DisplayName("une base d'avant la carte est situee et chargee d'offres")
    void legacyDataIsLocatedAndGivenOffers() {
        run();
        int products = savedProducts.size();

        Map<Long, Long> stockBefore = new HashMap<>();
        for (Product product : savedProducts) {
            product.setLocation(null);
            product.setLatitude(null);
            product.setLongitude(null);
            product.setGeoPrecision(null);
            stockBefore.put(product.getId(), product.getQuantityAvailable());
        }
        savedOffers.clear();
        savedHistory.clear();

        run();

        assertEquals(products, savedProducts.size(), "des annonces ont ete recreees");
        for (Product product : savedProducts) {
            assertNotNull(product.getLocation(), product.getTitle() + ": toujours sans moughataa");
            assertTrue(GeoSupport.isValid(product.getLatitude(), product.getLongitude()),
                    product.getTitle() + ": toujours sans coordonnees");
            assertTrue(product.getQuantityAvailable() <= stockBefore.get(product.getId()),
                    product.getTitle() + ": le stock a augmente");
        }
        assertFalse(savedOffers.isEmpty(), "aucune offre posee sur les annonces existantes");
    }

    // --- Offres ----------------------------------------------------------

    @Test
    @DisplayName("aucune offre n'est posee par le proprietaire de l'annonce")
    void nobodyBidsOnTheirOwnProduct() {
        run();

        assertFalse(savedOffers.isEmpty(), "le seeder doit creer des offres");
        for (Negotiation offer : savedOffers) {
            User owner = offer.getProduct().getUser();
            assertAll(offer.getProduct().getTitle(),
                    () -> assertNotEquals(owner.getId(), offer.getSender().getId(),
                            "l'auteur de l'offre est le vendeur"),
                    () -> assertEquals(owner.getId(), offer.getReceiver().getId(),
                            "le destinataire n'est pas le proprietaire de l'annonce"));
        }
    }

    @Test
    @DisplayName("les quatre etats d'une offre sont representes")
    void everyOfferStatusIsShown() {
        run();

        List<String> statuses = savedOffers.stream().map(Negotiation::getStatus).distinct().toList();
        assertTrue(statuses.contains(NegotiationStatus.STATUS_PENDING), "aucune offre en attente");
        assertTrue(statuses.contains(NegotiationStatus.STATUS_ACCEPTED), "aucune offre acceptee");
        assertTrue(statuses.contains(NegotiationStatus.STATUS_REJECTED), "aucune offre refusee");
        assertTrue(statuses.contains(NegotiationStatus.STATUS_CANCELLED), "aucune offre annulee");
    }

    /**
     * Le stock affiche doit etre celui qu'aurait laisse {@code acceptBySeller}:
     * une annonce qui montre 600 kg alors que 200 ont ete vendus refuserait la
     * premiere offre reelle avec "quantite superieure au stock".
     */
    @Test
    @DisplayName("le stock restant tient compte des offres acceptees")
    void acceptedOffersAreDeductedFromStock() {
        run();

        Map<Long, Long> acceptedByProduct = new HashMap<>();
        for (Negotiation offer : savedOffers) {
            if (NegotiationStatus.STATUS_ACCEPTED.equalsIgnoreCase(offer.getStatus())) {
                acceptedByProduct.merge(offer.getProduct().getId(),
                        offer.getQuantity().longValue(), Long::sum);
            }
        }
        assertFalse(acceptedByProduct.isEmpty(), "aucune offre acceptee a verifier");

        for (Product product : savedProducts) {
            long accepted = acceptedByProduct.getOrDefault(product.getId(), 0L);
            assertTrue(product.getQuantityAvailable() >= 0,
                    product.getTitle() + ": stock negatif");
            assertTrue(product.getQuantityAvailable() + accepted <= product.getQuantityTotal(),
                    product.getTitle() + ": le stock restant et les ventes depassent le total");
        }
    }

    @Test
    @DisplayName("aucune offre en attente ne depasse le stock restant")
    void pendingOffersFitInRemainingStock() {
        run();

        for (Negotiation offer : savedOffers) {
            if (!NegotiationStatus.STATUS_PENDING.equalsIgnoreCase(offer.getStatus())) {
                continue;
            }
            Product product = offer.getProduct();
            assertTrue(offer.getQuantity() <= product.getQuantityAvailable(),
                    product.getTitle() + ": offre de " + offer.getQuantity()
                            + " pour un stock de " + product.getQuantityAvailable());
        }
    }

    /**
     * Le fil est ce que l'application affiche a l'ouverture d'une negociation:
     * une offre sans premier tour s'y presente vide.
     */
    @Test
    @DisplayName("chaque offre ouvre son fil de negociation")
    void everyOfferOpensItsThread() {
        run();

        for (Negotiation offer : savedOffers) {
            boolean hasOpening = savedHistory.stream()
                    .anyMatch(entry -> entry.getNegotiation() == offer
                            && "OFFER".equals(entry.getKind())
                            && entry.getAuthor().getId().equals(offer.getSender().getId()));
            assertTrue(hasOpening, offer.getProduct().getTitle() + ": fil sans offre d'ouverture");
        }
    }

    @Test
    @DisplayName("une contre-proposition est signee par le vendeur")
    void counterOffersComeFromTheSeller() {
        run();

        List<NegotiationHistory> counters = savedHistory.stream()
                .filter(entry -> "COUNTER_OFFER".equals(entry.getKind()))
                .toList();
        assertFalse(counters.isEmpty(), "aucune contre-proposition dans le jeu de donnees");

        for (NegotiationHistory counter : counters) {
            Long ownerId = counter.getNegotiation().getProduct().getUser().getId();
            assertEquals(ownerId, counter.getAuthor().getId(),
                    "une contre-proposition ne peut venir que du vendeur");
        }
    }

    // --- Langues ---------------------------------------------------------

    /**
     * Les notifications sont traduites: un jeu de donnees monolingue ne
     * permettrait pas de le montrer, ni de reperer une traduction manquante.
     */
    @Test
    @DisplayName("les comptes ne parlent pas tous la meme langue")
    void demoAccountsSpeakSeveralLanguages() {
        run();

        List<String> languages = savedUsers.stream()
                .map(User::getPreferredLanguage).distinct().toList();
        assertTrue(languages.size() > 1,
                "tous les comptes de demonstration sont dans la meme langue");
        for (String language : languages) {
            assertTrue(SupportedLanguage.parse(language).isPresent(),
                    "langue non supportee: " + language);
        }
    }
}
