package com.project.RecyConnect.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.RecyConnect.Model.Category;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.ProductStatus;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.CategoryRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les routes dont depend le panneau d'administration.
 *
 * <p>Chacune corrige un comportement qui rendait une fonctionnalite inerte ou,
 * pire, faussement reussie: creer un compte levait une exception, une promotion
 * ne changeait rien, et n'importe quel utilisateur connecte pouvait vider le
 * catalogue des categories.
 */
@SpringBootTest
@AutoConfigureMockMvc
/*
 * Chaque test est annule a la fin plutot que de vider les tables au debut:
 * l'amorcage de demonstration cree des notifications qui referencent les
 * comptes, et un `deleteAll` sur `users` butait sur cette contrainte.
 */
@Transactional
class AdminEndpointsTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepo users;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private PasswordEncoder passwordEncoder;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = users.save(User.builder()
                .username("admin-test")
                .phone(22299999L)
                .pwd(passwordEncoder.encode("motdepasse"))
                .role(Role.ADMIN)
                .build());
    }

    private String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    // ------------------------------------------------------------ comptes

    @Test
    @DisplayName("POST /api/users cree un compte utilisable et ne renvoie jamais le mot de passe")
    @WithMockUser(username = "admin-test", roles = "ADMIN")
    void creerUnCompte() throws Exception {
        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "username", "Karim",
                                "phone", 44112233L,
                                "password", "secret123",
                                "role", "USER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("Karim"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").exists())
                /* Le mot de passe est en ecriture seule: il ne ressort pas. */
                .andExpect(jsonPath("$.password").doesNotExist());

        User created = users.findByPhone(44112233L);
        assertNotNull(created, "le compte doit exister en base");
        assertTrue(passwordEncoder.matches("secret123", created.getPassword()),
                "le mot de passe doit etre hache, et correspondre a la saisie");
    }

    @Test
    @DisplayName("un numero deja pris rend 409, pas une erreur serveur")
    @WithMockUser(username = "admin-test", roles = "ADMIN")
    void numeroDejaPris() throws Exception {
        users.save(User.builder().username("Deja").phone(44112233L)
                .pwd(passwordEncoder.encode("x")).role(Role.USER).build());

        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "Autre", "phone", 44112233L,
                                "password", "secret123", "role", "USER"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("l'indicatif 222 n'est retire que du format international")
    @WithMockUser(username = "admin-test", roles = "ADMIN")
    void indicatifRetireSeulementSurLeFormatInternational() throws Exception {
        /* 22233344 est un numero local valide: l'amputer donnait 33344, et le
           compte devenait introuvable a la connexion. */
        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "Local", "phone", 22233344L,
                                "password", "secret123", "role", "USER"))))
                .andExpect(status().isCreated());
        assertNotNull(users.findByPhone(22233344L), "le numero local reste entier");

        /* La forme internationale, elle, est bien raccourcie. */
        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "International", "phone", 22244556677L,
                                "password", "secret123", "role", "USER"))))
                .andExpect(status().isCreated());
        assertNotNull(users.findByPhone(44556677L), "222 + 8 chiffres est raccourci");
    }

    @Test
    @DisplayName("PATCH /api/users/{id}/password remplace le mot de passe")
    @WithMockUser(username = "admin-test", roles = "ADMIN")
    void reinitialiserUnMotDePasse() throws Exception {
        User cible = users.save(User.builder().username("Bloque").phone(44000111L)
                .pwd(passwordEncoder.encode("ancien")).role(Role.USER).build());

        mvc.perform(patch("/api/users/" + cible.getId() + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("password", "nouveau123"))))
                .andExpect(status().isNoContent());

        User apres = users.findById(cible.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("nouveau123", apres.getPassword()));
    }

    @Test
    @DisplayName("un mot de passe trop court est refuse en 400")
    @WithMockUser(username = "admin-test", roles = "ADMIN")
    void motDePasseTropCourt() throws Exception {
        mvc.perform(patch("/api/users/" + admin.getId() + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("password", "123"))))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------- roles

    @Test
    @DisplayName("PUT et PATCH /api/users/{id}/role changent bien le role")
    @WithMockUser(username = "admin-test", roles = "ADMIN")
    void changerUnRole() throws Exception {
        User cible = users.save(User.builder().username("Promu").phone(44002222L)
                .pwd(passwordEncoder.encode("x")).role(Role.USER).build());

        mvc.perform(put("/api/users/" + cible.getId() + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("role", "ADMIN"))))
                .andExpect(status().isOk());
        assertEquals(Role.ADMIN, users.findById(cible.getId()).orElseThrow().getRole());

        mvc.perform(patch("/api/users/" + cible.getId() + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("role", "USER"))))
                .andExpect(status().isOk());
        assertEquals(Role.USER, users.findById(cible.getId()).orElseThrow().getRole());
    }

    @Test
    @DisplayName("un administrateur ne peut pas se retirer ses propres droits")
    @WithMockUser(username = "admin-test", roles = "ADMIN")
    void pasDAutoRetrogradation() throws Exception {
        mvc.perform(put("/api/users/" + admin.getId() + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("role", "USER"))))
                .andExpect(status().isConflict());

        assertEquals(Role.ADMIN, users.findById(admin.getId()).orElseThrow().getRole(),
                "sans cette garde, un panneau sans administrateur reste possible");
    }

    // ---------------------------------------------------- mot de passe perso

    @Test
    @DisplayName("un ancien mot de passe faux rend 400, jamais 401")
    @WithMockUser(username = "admin-test", roles = "ADMIN")
    void ancienMotDePasseFaux() throws Exception {
        /* 401 signifie "session expiree" pour les clients, qui deconnecteraient
           l'operateur pour une simple faute de frappe. */
        mvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", "faux",
                                "newPassword", "nouveau123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("avec le bon ancien mot de passe, le nouveau est enregistre")
    @WithMockUser(username = "admin-test", roles = "ADMIN")
    void changerSonMotDePasse() throws Exception {
        mvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", "motdepasse",
                                "newPassword", "nouveau123"))))
                .andExpect(status().isNoContent());

        User apres = users.findById(admin.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("nouveau123", apres.getPassword()));
    }

    @Test
    @DisplayName("sans authentification, le changement de mot de passe rend 401")
    void changerSonMotDePasseSansJeton() throws Exception {
        mvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", "motdepasse",
                                "newPassword", "nouveau123"))))
                .andExpect(status().isUnauthorized());
    }

    // --------------------------------------------------------- categories

    @Test
    @DisplayName("ecrire une categorie exige le role ADMIN")
    @WithMockUser(username = "simple", roles = "USER")
    void categoriesReserveesAuxAdmins() throws Exception {
        mvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Verre"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("supprimer une categorie encore utilisee rend 409 et n'efface rien")
    @WithMockUser(username = "admin-test", roles = "ADMIN")
    void categorieUtiliseeNonSupprimable() throws Exception {
        Category categorie = categories.save(Category.builder().name("Verre").build());
        products.save(Product.builder()
                .title("Bouteilles")
                .createdAt(OffsetDateTime.now())
                .status(ProductStatus.AVAILABLE)
                .category(categorie)
                .user(admin)
                .build());

        mvc.perform(delete("/api/categories/" + categorie.getId()))
                .andExpect(status().isConflict());

        assertTrue(categories.findById(categorie.getId()).isPresent(),
                "les annonces rattachees ne doivent pas perdre leur categorie");
    }

    // -------------------------------------------------------------- photos

    @Test
    @DisplayName("les photos s'envoient sur /api/files/upload, pas sur /api/files")
    @WithMockUser(username = "admin-test", roles = "ADMIN")
    void envoiDePhoto() throws Exception {
        MockMultipartFile photo = new MockMultipartFile(
                "file", "annonce.png", MediaType.IMAGE_PNG_VALUE, new byte[] { 1, 2, 3 });

        mvc.perform(multipart("/api/files/upload").file(photo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists())
                .andExpect(jsonPath("$.filename").exists());

        /* La racine n'a aucun handler: c'est ce 4xx que le panneau lisait comme
           "capacite absente", d'ou "Action indisponible" a l'ajout d'une photo. */
        mvc.perform(multipart("/api/files").file(photo))
                .andExpect(result -> assertTrue(
                        result.getResponse().getStatus() >= 400,
                        "poster sur /api/files ne doit pas etre confondu avec un envoi"));
    }

    // ------------------------------------------------------------ annonces

    @Test
    @DisplayName("un administrateur publie au nom du vendeur qu'il designe")
    @WithMockUser(username = "admin-test", roles = "ADMIN")
    void publierPourUnVendeur() throws Exception {
        Category categorie = categories.save(Category.builder().name("Verre").build());
        User vendeur = users.save(User.builder().username("Vendeur").phone(44003333L)
                .pwd(passwordEncoder.encode("x")).role(Role.USER).build());

        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "title", "Bouteilles PET",
                                "desc", "lot",
                                "price", 25.0,
                                "unit", "KG",
                                "quantityTotal", 100,
                                "quantityAvailable", 80,
                                "status", "pending",
                                "categoryId", categorie.getId(),
                                "userId", vendeur.getId()))))
                .andExpect(status().isCreated())
                /* Ecrase sans distinction, ce champ rattachait au compte admin
                   toutes les annonces saisies pour autrui. */
                .andExpect(jsonPath("$.userId").value(vendeur.getId()))
                /* Le statut demande n'est pas repris tel quel: une annonce
                   creee en "pending" n'apparaissait dans aucune recherche, et
                   rien nulle part ne la faisait passer a "available". C'est le
                   serveur qui tranche desormais — voir
                   ProductService.publishableStatus. */
                .andExpect(jsonPath("$.status").value("available"))
                .andExpect(jsonPath("$.unit").value("KG"));
    }

    @Test
    @DisplayName("un statut inconnu est refuse: \"sold\" n'existe pas")
    @WithMockUser(username = "admin-test", roles = "ADMIN")
    void statutInconnuRefuse() throws Exception {
        Category categorie = categories.save(Category.builder().name("Verre").build());
        Product annonce = products.save(Product.builder()
                .title("Bouteilles").createdAt(OffsetDateTime.now())
                .status(ProductStatus.PENDING).category(categorie).user(admin).build());

        mvc.perform(patch("/api/products/" + annonce.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"sold\"}"))
                .andExpect(status().isBadRequest());

        for (String statut : new String[] { "pending", "available", "recycled", "archived" }) {
            mvc.perform(patch("/api/products/" + annonce.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"" + statut + "\"}"))
                    .andExpect(status().isOk());
        }
    }
}
