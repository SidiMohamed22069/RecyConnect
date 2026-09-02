package com.project.RecyConnect.Config;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.project.RecyConnect.Model.Category;
import com.project.RecyConnect.Model.GeoPrecision;
import com.project.RecyConnect.Model.Moughataa;
import com.project.RecyConnect.Model.Negotiation;
import com.project.RecyConnect.Model.NegotiationHistory;
import com.project.RecyConnect.Model.NegotiationStatus;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.ProductStatus;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.SupportedLanguage;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.CategoryRepository;
import com.project.RecyConnect.Repository.NegotiationHistoryRepository;
import com.project.RecyConnect.Repository.NegotiationRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.UserRepo;
import com.project.RecyConnect.Service.GeoSupport;

/**
 * Remplit la base de donnees de demonstration: quatre comptes, dix annonces
 * situees dans Nouakchott, et les offres qui les animent.
 *
 * <p>Contrairement au catalogue des categories, il ne s'agit pas de donnees de
 * reference: le seeder est donc <em>desactive par defaut</em> et ne s'allume
 * qu'a la demande, via {@code app.demo-seed.enabled=true}. Des annonces fictives
 * apparaissant seules sur un environnement ouvert au public seraient prises pour
 * de vraies offres.
 *
 * <p>Trois choses distinguent ce jeu de donnees d'une simple liste d'annonces,
 * et chacune existe parce qu'un ecran de l'application resterait vide sans elle:
 *
 * <ul>
 *   <li><b>Toutes les annonces sont situees</b>, moughataa <em>et</em> point
 *       GPS. Une annonce sans coordonnees n'apparait ni sur la carte ni dans
 *       "Pres de vous": une demonstration faite avec l'ancien jeu de donnees
 *       montrait une carte vide. Les huit moughataas de Nouakchott sont
 *       couvertes, jamais {@link Moughataa#AUTRE} — la zone "ailleurs" n'a pas
 *       de centre, donc rien a afficher.</li>
 *   <li><b>Les deux valeurs de {@link GeoPrecision} sont representees</b>, pour
 *       qu'une demonstration montre la difference entre un point exact et une
 *       zone floutee a 300 m sans avoir a creer une annonce a la main.</li>
 *   <li><b>Les comptes ne parlent pas tous la meme langue.</b> Les
 *       notifications sont traduites depuis peu; quatre comptes francophones
 *       n'en montreraient jamais rien.</li>
 *   <li><b>Les annonces portent des offres</b>, dans les quatre etats qu'un
 *       vendeur rencontre. Sans elles, l'onglet "Offres", le fil de
 *       negociation, le journal des transactions et les statistiques du profil
 *       public s'ouvrent tous sur un ecran vide.</li>
 * </ul>
 *
 * <p>Il est idempotent et n'ecrase rien: un compte de demonstration deja present
 * est reutilise, et ses annonces ne sont creees que s'il n'en possede aucune.
 * Un redemarrage ne duplique donc pas le jeu de donnees, et les annonces
 * supprimees a la main pendant une demonstration reviennent au demarrage
 * suivant.
 *
 * <p>Si le numero d'un compte de demonstration appartient deja a quelqu'un
 * d'autre, ce compte est ignore: on ne rattache pas des annonces fictives a un
 * utilisateur reel.
 */
@Component
@Order(20)
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    /**
     * Un compte de demonstration. Le numero sert de cle de connexion.
     *
     * @param language la langue des notifications de ce compte. Le jeu de
     *                 donnees en melange plusieurs: une demonstration faite
     *                 avec quatre comptes francophones ne montrerait jamais que
     *                 les notifications sont traduites.
     */
    private record DemoUser(String username, Long phone, SupportedLanguage language) {}

    /**
     * Une annonce de demonstration, rattachee a un compte et a une categorie.
     *
     * <p>La position n'est pas donnee en degres mais comme un decalage en
     * kilometres depuis le centre de {@link #moughataa()}. Une annonce tombe
     * ainsi toujours dans le quartier qu'elle declare — un couple de degres
     * saisi a la main finit tot ou tard par contredire son badge de lieu, et
     * l'incoherence ne se voit qu'une fois sur la carte.
     *
     * @param quantityAvailable stock avant les offres de {@link #OFFERS}; ce que
     *                          les offres acceptees en retirent est calcule, pas
     *                          saisi.
     */
    private record DemoProduct(
            String ownerUsername,
            String categoryCode,
            String title,
            String description,
            Double price,
            String unit,
            Long quantityTotal,
            Long quantityAvailable,
            ProductStatus status,
            int ageInDays,
            Moughataa moughataa,
            double northKm,
            double eastKm,
            GeoPrecision geoPrecision) {}

    /**
     * Une offre de demonstration, posee par un acheteur sur une annonce.
     *
     * <p>Le vendeur n'est pas indique: c'est toujours le proprietaire de
     * l'annonce, comme dans {@code NegotiationService}. Le repeter ici
     * permettrait de le contredire.
     *
     * @param counterPrice prix d'une contre-proposition du vendeur, ou
     *                     {@code null}. Ne sert qu'a remplir le fil de la
     *                     negociation, pas a changer l'offre elle-meme.
     */
    private record DemoOffer(
            String productTitle,
            String buyerUsername,
            Double price,
            Integer quantity,
            String status,
            int ageInDays,
            Double counterPrice) {}

    /**
     * Quatre comptes: trois qui vendent, un collecteur qui achete.
     *
     * <p>Le collecteur existe pour que les offres viennent de quelqu'un dont ce
     * soit le metier. Sans lui, chaque offre serait posee par un vendeur sur
     * l'annonce d'un autre, et la demonstration ne montrerait jamais le cas le
     * plus courant de la place de marche: un professionnel qui ne publie rien
     * et ne fait que ramasser.
     */
    private static final List<DemoUser> USERS = List.of(
            new DemoUser("Sidi Mohamed Ould Ahmed", 36241590L, SupportedLanguage.FR),
            new DemoUser("Mariem Mint Abdellahi", 44352718L, SupportedLanguage.AR),
            new DemoUser("Ahmedou Ould Cheikhna", 26810493L, SupportedLanguage.FR),
            new DemoUser("Khadijetou Mint Baba", 32549076L, SupportedLanguage.EN));

    private static final String COLLECTOR = "Khadijetou Mint Baba";

    /**
     * Les unites reprennent exactement celles du formulaire de depot de
     * l'application mobile (KG, METRE, UNIT): une valeur libre s'afficherait
     * telle quelle, non traduite, a cote d'annonces reelles.
     */
    private static final List<DemoProduct> PRODUCTS = List.of(
            new DemoProduct("Sidi Mohamed Ould Ahmed", "PLASTIC",
                    "Bouteilles plastique PET triées",
                    "Bouteilles d'eau et de boisson lavées et compactées, collectées au marché Capitale.",
                    25.0, "KG", 400L, 400L, ProductStatus.AVAILABLE, 1,
                    Moughataa.KSAR, 0.6, -0.4, GeoPrecision.EXACT),
            new DemoProduct("Mariem Mint Abdellahi", "PLASTIC",
                    "Bidons plastique de 20 litres",
                    "Bidons alimentaires vides, en bon état, réutilisables ou broyables.",
                    60.0, "UNIT", 120L, 95L, ProductStatus.AVAILABLE, 2,
                    Moughataa.TEVRAGH_ZEINA, -0.5, 0.8, GeoPrecision.APPROX),
            new DemoProduct("Sidi Mohamed Ould Ahmed", "PAPER",
                    "Cartons d'emballage aplatis",
                    "Cartons de grande surface, secs et aplatis, prêts pour le transport.",
                    15.0, "KG", 850L, 600L, ProductStatus.AVAILABLE, 3,
                    Moughataa.SEBKHA, 0.9, 0.3, GeoPrecision.EXACT),
            new DemoProduct("Ahmedou Ould Cheikhna", "PAPER",
                    "Archives papier de bureau",
                    "Papier blanc d'archives déclassées, sans reliure ni plastique.",
                    12.0, "KG", 300L, 300L, ProductStatus.AVAILABLE, 4,
                    Moughataa.TEVRAGH_ZEINA, 1.1, -0.6, GeoPrecision.APPROX),
            new DemoProduct("Mariem Mint Abdellahi", "IRON",
                    "Barres de fer à béton récupérées",
                    "Chutes de ferraillage issues d'un chantier de Tevragh Zeina, longueurs variables.",
                    45.0, "KG", 1200L, 1200L, ProductStatus.AVAILABLE, 5,
                    Moughataa.DAR_NAIM, -0.7, 0.5, GeoPrecision.EXACT),
            new DemoProduct("Ahmedou Ould Cheikhna", "IRON",
                    "Tôles et chutes métalliques",
                    "Tôles ondulées et découpes d'atelier, enlèvement sur place à la zone industrielle d'El Mina.",
                    38.0, "KG", 700L, 250L, ProductStatus.PENDING, 7,
                    Moughataa.EL_MINA, 0.4, 0.9, GeoPrecision.APPROX),
            new DemoProduct("Sidi Mohamed Ould Ahmed", "WOOD",
                    "Palettes en bois réutilisables",
                    "Palettes standard en bon état, idéales pour le stockage ou le mobilier.",
                    350.0, "UNIT", 60L, 60L, ProductStatus.AVAILABLE, 8,
                    Moughataa.ARAFAT, -0.3, -0.8, GeoPrecision.EXACT),
            new DemoProduct("Mariem Mint Abdellahi", "WOOD",
                    "Chutes de menuiserie",
                    "Lot vendu et enlevé. Conservé comme référence de prix du bois de récupération.",
                    8.0, "KG", 500L, 0L, ProductStatus.RECYCLED, 12,
                    Moughataa.TOUJOUNINE, 0.7, 0.2, GeoPrecision.APPROX),
            new DemoProduct("Ahmedou Ould Cheikhna", "ELECTRONICS",
                    "Câbles électriques en cuivre",
                    "Câbles dénudés et triés par section, cuivre rouge.",
                    180.0, "KG", 90L, 70L, ProductStatus.AVAILABLE, 9,
                    Moughataa.RIYAD, 1.2, 0.6, GeoPrecision.EXACT),
            new DemoProduct("Sidi Mohamed Ould Ahmed", "ELECTRONICS",
                    "Ordinateurs de bureau hors service",
                    "Unités centrales complètes, non fonctionnelles, pour récupération de composants.",
                    900.0, "UNIT", 25L, 25L, ProductStatus.AVAILABLE, 14,
                    Moughataa.KSAR, -0.9, 0.7, GeoPrecision.APPROX));

    /**
     * Les offres, dans les quatre etats qu'un vendeur rencontre.
     *
     * <p>Le detail qui compte: deux offres acceptees portent sur des annonces
     * encore {@code AVAILABLE}. C'est ce qui donne au collecteur un journal de
     * transactions et au vendeur un taux de reponse non nul, sans vider le
     * catalogue de ses annonces visibles.
     *
     * <p>Une offre acceptee ouvre aussi la position exacte du lot a son auteur
     * ({@code existsBySenderIdAndProductIdAndStatusIgnoreCase}): sans elle,
     * impossible de montrer en demonstration la difference entre ce que voit un
     * visiteur et ce que voit un acheteur retenu.
     */
    private static final List<DemoOffer> OFFERS = List.of(
            new DemoOffer("Bouteilles plastique PET triées", COLLECTOR,
                    22.0, 150, NegotiationStatus.STATUS_PENDING, 1, null),
            new DemoOffer("Bouteilles plastique PET triées", "Mariem Mint Abdellahi",
                    20.0, 400, NegotiationStatus.STATUS_PENDING, 1, 24.0),
            new DemoOffer("Cartons d'emballage aplatis", COLLECTOR,
                    14.0, 200, NegotiationStatus.STATUS_ACCEPTED, 2, null),
            new DemoOffer("Cartons d'emballage aplatis", "Ahmedou Ould Cheikhna",
                    11.0, 300, NegotiationStatus.STATUS_REJECTED, 2, null),
            new DemoOffer("Barres de fer à béton récupérées", COLLECTOR,
                    43.0, 500, NegotiationStatus.STATUS_ACCEPTED, 4, 46.0),
            new DemoOffer("Barres de fer à béton récupérées", "Sidi Mohamed Ould Ahmed",
                    40.0, 200, NegotiationStatus.STATUS_PENDING, 3, null),
            new DemoOffer("Câbles électriques en cuivre", COLLECTOR,
                    175.0, 40, NegotiationStatus.STATUS_PENDING, 5, null),
            new DemoOffer("Palettes en bois réutilisables", COLLECTOR,
                    300.0, 30, NegotiationStatus.STATUS_CANCELLED, 6, null),
            new DemoOffer("Ordinateurs de bureau hors service", COLLECTOR,
                    850.0, 10, NegotiationStatus.STATUS_PENDING, 10, null));

    private final UserRepo userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final NegotiationRepository negotiationRepository;
    private final NegotiationHistoryRepository negotiationHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String password;

    public DemoSeeder(
            UserRepo userRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            NegotiationRepository negotiationRepository,
            NegotiationHistoryRepository negotiationHistoryRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.demo-seed.enabled:false}") boolean enabled,
            @Value("${app.demo-seed.password:}") String password) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.negotiationRepository = negotiationRepository;
        this.negotiationHistoryRepository = negotiationHistoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        if (password == null || password.isBlank()) {
            log.warn("Seed de demonstration ignore: app.demo-seed.password est vide. "
                    + "Renseigner DEMO_SEED_PASSWORD ou desactiver app.demo-seed.enabled.");
            return;
        }

        Map<String, User> demoUsers = new LinkedHashMap<>();
        for (DemoUser demoUser : USERS) {
            resolve(demoUser).ifPresent(user -> demoUsers.put(demoUser.username(), user));
        }

        int createdProducts = seedProducts(demoUsers);

        // Les trois etapes suivantes travaillent sur les annonces de
        // demonstration *presentes en base*, pas seulement sur celles qui
        // viennent d'etre creees: une base de demonstration remplie avant la
        // carte n'en aurait recu aucune, et serait restee sans position ni
        // offre indefiniment.
        Map<String, Product> products = demoProductsByTitle(demoUsers);
        int located = backfillMissingLocations(products);
        int createdOffers = seedOffers(demoUsers, products);

        log.info("Seed de demonstration termine: {} compte(s) disponible(s), {} annonce(s) creee(s), "
                        + "{} annonce(s) situee(s) apres coup, {} offre(s) creee(s). "
                        + "Connexion: numero au format 222XXXXXXXX, mot de passe commun defini par app.demo-seed.password.",
                demoUsers.size(), createdProducts, located, createdOffers);
    }

    /**
     * Le compte correspondant a [demoUser], cree si besoin.
     *
     * <p>Vide si le numero est deja pris par quelqu'un d'autre: le numero est la
     * cle de connexion, et rattacher des annonces fictives a un compte reel
     * fausserait sa page profil.
     */
    private Optional<User> resolve(DemoUser demoUser) {
        User existing = userRepository.findByPhone(demoUser.phone());
        if (existing != null) {
            if (!demoUser.username().equals(existing.getUsername())) {
                log.warn("Compte de demonstration ignore: le numero {} appartient deja a '{}'.",
                        demoUser.phone(), existing.getUsername());
                return Optional.empty();
            }
            return Optional.of(existing);
        }

        // findByUsername alimente loadUserByUsername: un homonyme casserait
        // l'authentification des deux comptes.
        if (userRepository.findByUsername(demoUser.username()) != null) {
            log.warn("Compte de demonstration ignore: le nom '{}' est deja pris.", demoUser.username());
            return Optional.empty();
        }

        User created = userRepository.save(User.builder()
                .username(demoUser.username())
                .phone(demoUser.phone())
                .pwd(passwordEncoder.encode(password))
                .role(Role.USER)
                .preferredLanguage(demoUser.language().getCode())
                .imageData(User.DEFAULT_IMAGE_DATA)
                .build());

        log.info("Compte de demonstration cree: id={}, username='{}', phone={}.",
                created.getId(), created.getUsername(), created.getPhone());
        return Optional.of(created);
    }

    /**
     * Cree les annonces des comptes de [demoUsers] qui n'en possedent aucune.
     *
     * <p>Le stock ecrit ici est celui declare dans {@link #PRODUCTS}, avant
     * offres: c'est {@link #seedOffers} qui en retranche les ventes, pour que le
     * calcul n'existe qu'a un seul endroit.
     *
     * @return le nombre d'annonces creees.
     */
    private int seedProducts(Map<String, User> demoUsers) {
        Map<String, Category> categories = new LinkedHashMap<>();
        for (Category category : categoryRepository.findAll()) {
            if (category.getCode() != null && !category.getCode().isBlank()) {
                categories.putIfAbsent(category.getCode(), category);
            }
        }

        // Une seule vague d'annonces par compte: sans ce garde-fou, chaque
        // redemarrage ajouterait dix annonces de plus a la liste.
        Set<Long> alreadyStocked = new HashSet<>();
        for (User user : demoUsers.values()) {
            if (!productRepository.findByUserId(user.getId()).isEmpty()) {
                alreadyStocked.add(user.getId());
            }
        }

        int created = 0;
        for (DemoProduct demoProduct : PRODUCTS) {
            User owner = demoUsers.get(demoProduct.ownerUsername());
            if (owner == null || alreadyStocked.contains(owner.getId())) {
                continue;
            }

            Category category = categories.get(demoProduct.categoryCode());
            if (category == null) {
                log.warn("Annonce de demonstration '{}' ignoree: categorie {} absente du catalogue.",
                        demoProduct.title(), demoProduct.categoryCode());
                continue;
            }

            double[] point = pointOf(demoProduct);
            if (point == null) {
                log.warn("Annonce de demonstration '{}' creee sans coordonnees: la zone {} n'a pas de centre.",
                        demoProduct.title(), demoProduct.moughataa());
            }

            productRepository.save(Product.builder()
                    .title(demoProduct.title())
                    .description(demoProduct.description())
                    .price(demoProduct.price())
                    .unit(demoProduct.unit())
                    .quantityTotal(demoProduct.quantityTotal())
                    .quantityAvailable(demoProduct.quantityAvailable())
                    .status(demoProduct.status())
                    .location(demoProduct.moughataa())
                    .latitude(point != null ? point[0] : null)
                    .longitude(point != null ? point[1] : null)
                    .geoPrecision(point != null ? demoProduct.geoPrecision() : null)
                    // Aucune photo: les clients affichent alors leur visuel par
                    // defaut, plutot qu'un lien casse vers un fichier absent du
                    // dossier d'upload.
                    .imageUrls(new ArrayList<>())
                    .category(category)
                    .user(owner)
                    .createdAt(OffsetDateTime.now().minusDays(demoProduct.ageInDays()))
                    .build());
            created++;
        }
        return created;
    }

    /**
     * Les annonces de demonstration presentes en base, par titre.
     *
     * <p>Seules celles dont le titre figure dans {@link #PRODUCTS} et qui
     * appartiennent a un compte de demonstration: une annonce que quelqu'un a
     * publiee depuis un de ces comptes pendant une demonstration n'est ni
     * situee ni chargee d'offres.
     */
    private Map<String, Product> demoProductsByTitle(Map<String, User> demoUsers) {
        Set<String> knownTitles = new HashSet<>();
        for (DemoProduct demoProduct : PRODUCTS) {
            knownTitles.add(demoProduct.title());
        }

        Map<String, Product> products = new LinkedHashMap<>();
        for (User user : demoUsers.values()) {
            for (Product product : productRepository.findByUserId(user.getId())) {
                if (knownTitles.contains(product.getTitle())) {
                    products.putIfAbsent(product.getTitle(), product);
                }
            }
        }
        return products;
    }

    /**
     * Situe les annonces de demonstration qui ne le sont pas encore.
     *
     * <p>Les dix annonces d'origine ont ete publiees avant la carte et n'ont ni
     * moughataa ni coordonnees. Sans ce rattrapage, une base de demonstration
     * deja remplie garderait une carte vide indefiniment: {@link #seedProducts}
     * ne recree jamais les annonces d'un compte qui en possede.
     *
     * <p>Ne remplit que ce qui manque: une annonce que quelqu'un a situee a la
     * main pendant une demonstration n'est jamais deplacee.
     *
     * @return le nombre d'annonces situees.
     */
    private int backfillMissingLocations(Map<String, Product> products) {
        Map<String, DemoProduct> byTitle = new LinkedHashMap<>();
        for (DemoProduct demoProduct : PRODUCTS) {
            byTitle.put(demoProduct.title(), demoProduct);
        }

        int located = 0;
        for (Product product : products.values()) {
            DemoProduct expected = byTitle.get(product.getTitle());
            if (expected == null) {
                continue;
            }

            boolean missingZone = product.getLocation() == null;
            boolean missingPoint = !GeoSupport.isValid(product.getLatitude(), product.getLongitude());
            if (!missingZone && !missingPoint) {
                continue;
            }

            boolean changed = false;
            if (missingZone) {
                product.setLocation(expected.moughataa());
                changed = true;
            }
            double[] point = missingPoint ? pointOf(expected) : null;
            if (point != null) {
                product.setLatitude(point[0]);
                product.setLongitude(point[1]);
                product.setGeoPrecision(expected.geoPrecision());
                changed = true;
            }
            if (!changed) {
                continue;
            }
            productRepository.save(product);
            located++;
        }
        return located;
    }

    /**
     * Pose les offres de {@link #OFFERS} sur les annonces qui n'en ont aucune.
     *
     * <p>Chaque offre porte aussi son premier tour de fil ({@code OFFER}), et sa
     * contre-proposition quand elle en a une: le fil est ce que l'application
     * affiche a l'ouverture d'une negociation, et une negociation sans fil y
     * apparait vide.
     *
     * <p>C'est aussi ici qu'est ecrit le stock restant, <em>recalcule</em>
     * depuis la valeur declaree dans {@link #PRODUCTS} plutot que retranche de
     * la valeur en base. La difference compte: retrancher a chaque passage
     * ferait fondre le stock a chaque redemarrage ou les offres auraient ete
     * effacees, la ou un recalcul redonne toujours le meme resultat.
     *
     * @return le nombre d'offres creees.
     */
    private int seedOffers(Map<String, User> demoUsers, Map<String, Product> products) {
        Map<String, DemoProduct> byTitle = new LinkedHashMap<>();
        for (DemoProduct demoProduct : PRODUCTS) {
            byTitle.put(demoProduct.title(), demoProduct);
        }

        // Une annonce qui porte deja des offres est laissee telle quelle: sans ce
        // garde-fou, chaque redemarrage empilerait une nouvelle salve d'offres
        // sur la meme annonce. Le releve est fait ici, avant d'ecrire quoi que
        // ce soit: interroge dans la boucle, il verrait les offres que le
        // seeder vient lui-meme de poser et abandonnerait des la deuxieme.
        Set<String> alreadyNegotiated = new HashSet<>();
        for (Product product : products.values()) {
            if (!negotiationRepository.findByProductId(product.getId()).isEmpty()) {
                alreadyNegotiated.add(product.getTitle());
            }
        }

        int created = 0;
        Set<String> stocked = new HashSet<>();
        for (DemoOffer demoOffer : OFFERS) {
            Product product = products.get(demoOffer.productTitle());
            if (product == null || alreadyNegotiated.contains(product.getTitle())) {
                continue;
            }

            User buyer = demoUsers.get(demoOffer.buyerUsername());
            if (buyer == null) {
                continue;
            }

            User seller = product.getUser();
            if (seller.getId().equals(buyer.getId())) {
                log.warn("Offre de demonstration ignoree: '{}' ne peut pas encherir sur sa propre annonce '{}'.",
                        buyer.getUsername(), product.getTitle());
                continue;
            }

            OffsetDateTime createdAt = OffsetDateTime.now().minusDays(demoOffer.ageInDays());
            Negotiation offer = negotiationRepository.save(Negotiation.builder()
                    .sender(buyer)
                    .receiver(seller)
                    .product(product)
                    .price(demoOffer.price())
                    .quantity(demoOffer.quantity())
                    .status(demoOffer.status())
                    .createdAt(createdAt)
                    .build());

            negotiationHistoryRepository.save(NegotiationHistory.builder()
                    .negotiation(offer)
                    .author(buyer)
                    .kind("OFFER")
                    .price(demoOffer.price())
                    .quantity(demoOffer.quantity())
                    .createdAt(createdAt)
                    .build());

            if (demoOffer.counterPrice() != null) {
                negotiationHistoryRepository.save(NegotiationHistory.builder()
                        .negotiation(offer)
                        .author(seller)
                        .kind("COUNTER_OFFER")
                        .price(demoOffer.counterPrice())
                        .quantity(demoOffer.quantity())
                        .createdAt(createdAt.plusHours(3))
                        .build());
            }

            created++;
            stocked.add(product.getTitle());
        }

        for (String title : stocked) {
            applyAcceptedOffersToStock(products.get(title), byTitle.get(title));
        }
        return created;
    }

    /**
     * Retire du stock de [product] ce que ses offres acceptees ont vendu.
     *
     * <p>Reproduit exactement l'effet de {@code NegotiationService.acceptBySeller}:
     * une annonce qui afficherait 600 kg alors que 200 ont ete vendus refuserait
     * la premiere offre reelle avec "quantite superieure au stock".
     */
    private void applyAcceptedOffersToStock(Product product, DemoProduct declared) {
        if (product == null || declared == null) {
            return;
        }

        long sold = acceptedQuantityFor(declared.title());
        if (sold <= 0L) {
            return;
        }

        long available = Math.max(0L, declared.quantityAvailable() - sold);
        product.setQuantityAvailable(available);
        if (available <= 0L && product.getStatus() == ProductStatus.AVAILABLE) {
            product.setStatus(ProductStatus.RECYCLED);
        }
        productRepository.save(product);
    }

    /** La quantite retiree du stock de [productTitle] par ses offres acceptees. */
    private static long acceptedQuantityFor(String productTitle) {
        long sold = 0L;
        for (DemoOffer offer : OFFERS) {
            if (offer.productTitle().equals(productTitle)
                    && NegotiationStatus.STATUS_ACCEPTED.equalsIgnoreCase(offer.status())
                    && offer.quantity() != null) {
                sold += offer.quantity();
            }
        }
        return sold;
    }

    /**
     * Le point GPS de [demoProduct]: le centre de sa moughataa, decale.
     *
     * <p>{@code null} pour une zone sans centre — {@link Moughataa#AUTRE}. Le
     * seeder tourne au demarrage de l'application: une annonce de demonstration
     * mal declaree doit lui couter ses coordonnees, pas empecher le serveur de
     * demarrer.
     *
     * @return un couple {@code [latitude, longitude]}, ou {@code null}.
     */
    private static double[] pointOf(DemoProduct demoProduct) {
        Moughataa zone = demoProduct.moughataa();
        if (zone == null || !zone.hasCentroid()) {
            return null;
        }
        double latitude = zone.getCentroidLatitude()
                + GeoSupport.latitudeDegreesFor(demoProduct.northKm());
        double longitude = zone.getCentroidLongitude()
                + GeoSupport.longitudeDegreesFor(demoProduct.eastKm(), zone.getCentroidLatitude());
        return new double[] {latitude, longitude};
    }
}
