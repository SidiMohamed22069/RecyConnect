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
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.ProductStatus;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.CategoryRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.UserRepo;

/**
 * Remplit la base de donnees de demonstration: trois comptes et dix annonces.
 *
 * <p>Contrairement au catalogue des categories, il ne s'agit pas de donnees de
 * reference: le seeder est donc <em>desactive par defaut</em> et ne s'allume
 * qu'a la demande, via {@code app.demo-seed.enabled=true}. Des annonces fictives
 * apparaissant seules sur un environnement ouvert au public seraient prises pour
 * de vraies offres.
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

    /** Un compte de demonstration. Le numero sert de cle de connexion. */
    private record DemoUser(String username, Long phone) {}

    /** Une annonce de demonstration, rattachee a un compte et a une categorie. */
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
            int ageInDays) {}

    private static final List<DemoUser> USERS = List.of(
            new DemoUser("Sidi Mohamed Ould Ahmed", 36241590L),
            new DemoUser("Mariem Mint Abdellahi", 44352718L),
            new DemoUser("Ahmedou Ould Cheikhna", 26810493L));

    /**
     * Les unites reprennent exactement celles du formulaire de depot de
     * l'application mobile (KG, METRE, UNIT): une valeur libre s'afficherait
     * telle quelle, non traduite, a cote d'annonces reelles.
     */
    private static final List<DemoProduct> PRODUCTS = List.of(
            new DemoProduct("Sidi Mohamed Ould Ahmed", "PLASTIC",
                    "Bouteilles plastique PET triées",
                    "Bouteilles d'eau et de boisson lavées et compactées, collectées au marché Capitale.",
                    25.0, "KG", 400L, 400L, ProductStatus.AVAILABLE, 1),
            new DemoProduct("Mariem Mint Abdellahi", "PLASTIC",
                    "Bidons plastique de 20 litres",
                    "Bidons alimentaires vides, en bon état, réutilisables ou broyables.",
                    60.0, "UNIT", 120L, 95L, ProductStatus.AVAILABLE, 2),
            new DemoProduct("Sidi Mohamed Ould Ahmed", "PAPER",
                    "Cartons d'emballage aplatis",
                    "Cartons de grande surface, secs et aplatis, prêts pour le transport.",
                    15.0, "KG", 850L, 600L, ProductStatus.AVAILABLE, 3),
            new DemoProduct("Ahmedou Ould Cheikhna", "PAPER",
                    "Archives papier de bureau",
                    "Papier blanc d'archives déclassées, sans reliure ni plastique.",
                    12.0, "KG", 300L, 300L, ProductStatus.AVAILABLE, 4),
            new DemoProduct("Mariem Mint Abdellahi", "IRON",
                    "Barres de fer à béton récupérées",
                    "Chutes de ferraillage issues d'un chantier de Tevragh Zeina, longueurs variables.",
                    45.0, "KG", 1200L, 1200L, ProductStatus.AVAILABLE, 5),
            new DemoProduct("Ahmedou Ould Cheikhna", "IRON",
                    "Tôles et chutes métalliques",
                    "Tôles ondulées et découpes d'atelier, enlèvement sur place à Nouadhibou.",
                    38.0, "KG", 700L, 250L, ProductStatus.PENDING, 7),
            new DemoProduct("Sidi Mohamed Ould Ahmed", "WOOD",
                    "Palettes en bois réutilisables",
                    "Palettes standard en bon état, idéales pour le stockage ou le mobilier.",
                    350.0, "UNIT", 60L, 60L, ProductStatus.AVAILABLE, 8),
            new DemoProduct("Mariem Mint Abdellahi", "WOOD",
                    "Chutes de menuiserie",
                    "Lot vendu et enlevé. Conservé comme référence de prix du bois de récupération.",
                    8.0, "KG", 500L, 0L, ProductStatus.RECYCLED, 12),
            new DemoProduct("Ahmedou Ould Cheikhna", "ELECTRONICS",
                    "Câbles électriques en cuivre",
                    "Câbles dénudés et triés par section, cuivre rouge.",
                    180.0, "KG", 90L, 70L, ProductStatus.AVAILABLE, 9),
            new DemoProduct("Sidi Mohamed Ould Ahmed", "ELECTRONICS",
                    "Ordinateurs de bureau hors service",
                    "Unités centrales complètes, non fonctionnelles, pour récupération de composants.",
                    900.0, "UNIT", 25L, 25L, ProductStatus.AVAILABLE, 14));

    private final UserRepo userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String password;

    public DemoSeeder(
            UserRepo userRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.demo-seed.enabled:false}") boolean enabled,
            @Value("${app.demo-seed.password:}") String password) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
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

        log.info("Seed de demonstration termine: {} compte(s) disponible(s), {} annonce(s) creee(s). "
                        + "Connexion: numero au format 222XXXXXXXX, mot de passe commun defini par app.demo-seed.password.",
                demoUsers.size(), createdProducts);
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
                .imageData(User.DEFAULT_IMAGE_DATA)
                .build());

        log.info("Compte de demonstration cree: id={}, username='{}', phone={}.",
                created.getId(), created.getUsername(), created.getPhone());
        return Optional.of(created);
    }

    /**
     * Cree les annonces des comptes de [demoUsers] qui n'en possedent aucune.
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

            productRepository.save(Product.builder()
                    .title(demoProduct.title())
                    .description(demoProduct.description())
                    .price(demoProduct.price())
                    .unit(demoProduct.unit())
                    .quantityTotal(demoProduct.quantityTotal())
                    .quantityAvailable(demoProduct.quantityAvailable())
                    .status(demoProduct.status())
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
}
