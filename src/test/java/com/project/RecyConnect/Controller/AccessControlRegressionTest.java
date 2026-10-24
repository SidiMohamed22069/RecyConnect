package com.project.RecyConnect.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.RecyConnect.Model.*;
import com.project.RecyConnect.Repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ce qu'un compte ne doit pas voir ni faire chez les autres : numeros de
 * telephone, offres, notifications. Chaque test verrouille une fuite trouvee
 * a la revue pre-deploiement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccessControlRegressionTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepo users;
    @Autowired ProductRepository products;
    @Autowired NegotiationRepository negotiations;
    @Autowired NotificationRepository notifications;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;

    private User vendeur;
    private User acheteur;
    private User tiers;
    private Product annonce;
    private Negotiation offreAcheteur;
    private Negotiation offreTiers;
    private Notification notifVendeur;

    @BeforeEach
    void setUp() {
        vendeur = users.save(User.builder().username("acl-vendeur").phone(44220001L).pwd(encoder.encode("x")).role(Role.USER).build());
        acheteur = users.save(User.builder().username("acl-acheteur").phone(44220002L).pwd(encoder.encode("x")).role(Role.USER).build());
        tiers = users.save(User.builder().username("acl-tiers").phone(44220003L).pwd(encoder.encode("x")).role(Role.USER).build());
        annonce = products.save(Product.builder().title("Plastique").user(vendeur).status(ProductStatus.AVAILABLE)
                .price(5.0).quantityTotal(100L).quantityAvailable(100L).createdAt(OffsetDateTime.now()).build());
        offreAcheteur = negotiations.save(Negotiation.builder().sender(acheteur).receiver(vendeur).product(annonce)
                .status("pending").price(4.0).quantity(10).createdAt(OffsetDateTime.now()).build());
        offreTiers = negotiations.save(Negotiation.builder().sender(tiers).receiver(vendeur).product(annonce)
                .status("pending").price(4.5).quantity(10).createdAt(OffsetDateTime.now()).build());
        notifVendeur = notifications.save(Notification.builder().sender(acheteur).receiver(vendeur)
                .title("Offre").message("4 MRU").type("OFFER_RECEIVED").isRead(false)
                .createdAt(OffsetDateTime.now()).build());
    }

    // --- Numero du vendeur ---

    @Test
    @DisplayName("le catalogue anonyme ne porte pas le numero du vendeur")
    void numeroAbsentDuCatalogue() throws Exception {
        mvc.perform(get("/api/products/" + annonce.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userPhone").doesNotExist());
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userPhone").doesNotExist());
    }

    @Test
    @WithMockUser(username = "acl-vendeur", roles = "USER")
    @DisplayName("le proprietaire voit son propre numero sur sa fiche")
    void numeroVisibleParLeProprietaire() throws Exception {
        mvc.perform(get("/api/products/" + annonce.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userPhone").value(44220001L));
    }

    @Test
    @WithMockUser(username = "acl-tiers", roles = "USER")
    @DisplayName("GET /api/users/{id} d'un autre compte rend la fiche publique, sans numero")
    void ficheDUnAutreSansNumero() throws Exception {
        mvc.perform(get("/api/users/" + vendeur.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").doesNotExist());
        mvc.perform(get("/api/users/" + tiers.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(44220003L));
        mvc.perform(get("/api/users/by-phone/44220001"))
                .andExpect(status().isForbidden());
    }

    // --- Offres ---

    @Test
    @WithMockUser(username = "acl-tiers", roles = "USER")
    @DisplayName("une offre n'est lisible que par ses parties ; la liste globale est reservee a l'administration")
    void offresDesAutresInvisibles() throws Exception {
        mvc.perform(get("/api/negotiations")).andExpect(status().isForbidden());
        mvc.perform(get("/api/negotiations/" + offreAcheteur.getId())).andExpect(status().isForbidden());
        mvc.perform(get("/api/negotiations/sender/" + acheteur.getId())).andExpect(status().isForbidden());
        mvc.perform(get("/api/negotiations/receiver/" + vendeur.getId())).andExpect(status().isForbidden());
        mvc.perform(get("/api/negotiations/" + offreTiers.getId())).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "acl-acheteur", roles = "USER")
    @DisplayName("sur une annonce, un acheteur ne voit que ses propres offres ; le vendeur les voit toutes")
    void offresParAnnonceFiltrees() throws Exception {
        mvc.perform(get("/api/negotiations/product/" + annonce.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].senderId").value(acheteur.getId()));
        mvc.perform(get("/api/negotiations/product/" + annonce.getId() + "/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @WithMockUser(username = "acl-vendeur", roles = "USER")
    void leVendeurVoitToutesLesOffres() throws Exception {
        mvc.perform(get("/api/negotiations/product/" + annonce.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "acl-acheteur", roles = "USER")
    @DisplayName("PATCH ne permet plus a l'acheteur d'accepter sa propre offre")
    void patchNeChangePasLeStatut() throws Exception {
        mvc.perform(patch("/api/negotiations/" + offreAcheteur.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", "accepted", "price", 3.5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.price").value(3.5));
    }

    @Test
    @WithMockUser(username = "acl-tiers", roles = "USER")
    @DisplayName("une annonce en pause n'accepte plus d'offres (409)")
    void annonceEnPauseRefuseLesOffres() throws Exception {
        annonce.setStatus(ProductStatus.PAUSED);
        products.save(annonce);
        mvc.perform(post("/api/negotiations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "productId", annonce.getId(), "price", 4.0, "quantity", 5))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    // --- Notifications ---

    @Test
    @WithMockUser(username = "acl-acheteur", roles = "USER")
    @DisplayName("la boite d'un autre compte est fermee en lecture, suppression et creation")
    void notificationsDesAutresFermees() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications/receiver/" + vendeur.getId())).andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications/receiver/" + vendeur.getId() + "/unread/count")).andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications/" + notifVendeur.getId())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/notifications/" + notifVendeur.getId())).andExpect(status().isForbidden());
        mvc.perform(patch("/api/notifications/" + notifVendeur.getId() + "/read")).andExpect(status().isForbidden());
        mvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "receiverId", vendeur.getId(), "title", "x", "message", "y", "type", "BROADCAST"))))
                .andExpect(status().isForbidden());
        // sa propre boite, elle, reste lisible
        mvc.perform(get("/api/notifications/receiver/" + acheteur.getId())).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "acl-vendeur", roles = "USER")
    void leDestinataireLitEtMarqueLue() throws Exception {
        mvc.perform(get("/api/notifications/receiver/" + vendeur.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(patch("/api/notifications/" + notifVendeur.getId() + "/read")).andExpect(status().isOk());
    }

    // --- Validation ---

    @Test
    @WithMockUser(username = "acl-vendeur", roles = "USER")
    @DisplayName("une description trop longue est refusee en 400 avec un message, pas en 500")
    void descriptionTropLongue() throws Exception {
        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "title", "Verre", "desc", "x".repeat(2001), "price", 1.0,
                                "quantityTotal", 1, "quantityAvailable", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
