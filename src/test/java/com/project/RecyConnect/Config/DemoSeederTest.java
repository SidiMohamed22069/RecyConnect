package com.project.RecyConnect.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.project.RecyConnect.Model.Category;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.CategoryRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.UserRepo;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DemoSeederTest {

    private static final String PASSWORD = "demo1234";

    @Mock
    private UserRepo userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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
        when(userRepository.findByPhone(anyLong())).thenReturn(null);
        when(userRepository.findByUsername(anyString())).thenReturn(null);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(nextId.getAndIncrement());
            return user;
        });
        when(productRepository.findByUserId(anyLong())).thenReturn(new ArrayList<>());
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
    }

    private DemoSeeder seeder(boolean enabled, String password) {
        return new DemoSeeder(userRepository, categoryRepository, productRepository,
                passwordEncoder, enabled, password);
    }

    private List<User> savedUsers() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private List<Product> savedProducts() {
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("Cree trois comptes et dix annonces sur une base vide")
    void seedsThreeUsersAndTenProducts() {
        seeder(true, PASSWORD).run(null);

        List<User> users = savedUsers();
        assertEquals(3, users.size());
        for (User user : users) {
            assertEquals(Role.USER, user.getRole());
            assertNotNull(user.getPhone());
            assertNotEquals(PASSWORD, user.getPwd(), "Le mot de passe ne doit jamais etre stocke en clair");
            assertTrue(passwordEncoder.matches(PASSWORD, user.getPwd()));
        }

        List<Product> products = savedProducts();
        assertEquals(10, products.size());
        for (Product product : products) {
            assertNotNull(product.getCategory(), "Chaque annonce est classee");
            assertNotNull(product.getUser(), "Chaque annonce a un vendeur");
            assertNotNull(product.getStatus());
            assertNotNull(product.getCreatedAt());
            assertTrue(product.getQuantityAvailable() <= product.getQuantityTotal(),
                    "La quantite disponible ne depasse jamais le total: " + product.getTitle());
            assertTrue(product.getImageUrls().isEmpty(), "Le jeu de demonstration ne porte aucune photo");
        }
    }

    /**
     * Les unites doivent etre celles du formulaire mobile: une valeur libre
     * s'afficherait non traduite a cote des annonces reelles.
     */
    @Test
    @DisplayName("N'utilise que les unites connues du formulaire mobile")
    void usesOnlyKnownUnits() {
        seeder(true, PASSWORD).run(null);

        for (Product product : savedProducts()) {
            assertTrue(List.of("KG", "METRE", "UNIT").contains(product.getUnit()),
                    "Unite inconnue: " + product.getUnit());
        }
    }

    @Test
    @DisplayName("Couvre les cinq categories du catalogue")
    void spreadsProductsAcrossCatalogue() {
        seeder(true, PASSWORD).run(null);

        List<String> codes = savedProducts().stream()
                .map(p -> p.getCategory().getCode())
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
     * de dix annonces a chaque relance.
     */
    @Test
    @DisplayName("Un redemarrage ne duplique ni comptes ni annonces")
    void isIdempotentAcrossRestarts() {
        AtomicLong nextId = new AtomicLong(1);
        List<User> existing = new ArrayList<>();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(nextId.getAndIncrement());
            existing.add(user);
            return user;
        });
        when(userRepository.findByPhone(anyLong())).thenAnswer(invocation -> existing.stream()
                .filter(u -> invocation.getArgument(0).equals(u.getPhone()))
                .findFirst()
                .orElse(null));

        seeder(true, PASSWORD).run(null);

        // Deuxieme demarrage: les comptes existent et possedent deja leurs annonces.
        when(productRepository.findByUserId(anyLong()))
                .thenReturn(List.of(Product.builder().id(1L).build()));

        seeder(true, PASSWORD).run(null);

        assertEquals(3, savedUsers().size(), "Aucun compte recree");
        assertEquals(10, savedProducts().size(), "Aucune annonce dupliquee");
    }

    /**
     * Le numero est la cle de connexion. S'il appartient a quelqu'un d'autre,
     * lui accrocher des annonces fictives fausserait son profil.
     */
    @Test
    @DisplayName("Ignore un compte dont le numero appartient a un vrai utilisateur")
    void skipsUserWhosePhoneBelongsToSomeoneElse() {
        User realUser = User.builder().id(99L).username("Vrai Utilisateur").phone(36241590L).build();
        when(userRepository.findByPhone(36241590L)).thenReturn(realUser);

        seeder(true, PASSWORD).run(null);

        assertEquals(2, savedUsers().size());
        for (Product product : savedProducts()) {
            assertNotEquals(99L, product.getUser().getId(),
                    "Aucune annonce de demonstration sur un compte reel");
        }
    }

    @Test
    @DisplayName("N'invente aucune annonce quand le catalogue est vide")
    void skipsProductsWhenCatalogueMissing() {
        when(categoryRepository.findAll()).thenReturn(new ArrayList<>());

        seeder(true, PASSWORD).run(null);

        assertEquals(3, savedUsers().size());
        verify(productRepository, never()).save(any(Product.class));
    }
}
