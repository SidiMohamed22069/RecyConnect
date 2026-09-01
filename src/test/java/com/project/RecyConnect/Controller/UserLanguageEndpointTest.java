package com.project.RecyConnect.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code PUT /api/users/me/language} vu du reseau.
 *
 * <p>La route est sous {@code /me} et non sous {@code /{id}}: la cible vient du
 * jeton, jamais de l'URL. Les tests ci-dessous verifient qu'il n'existe donc
 * aucune forme de la requete permettant de changer la langue d'un autre compte,
 * et qu'une langue inconnue est refusee plutot que silencieusement ignoree — le
 * mobile doit apprendre qu'il a demande l'impossible.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserLanguageEndpointTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepo users;
    @Autowired private PasswordEncoder passwordEncoder;

    private User appelant;
    private User autre;

    @BeforeEach
    void setUp() {
        appelant = users.save(User.builder()
                .username("langue-test")
                .phone(44881122L)
                .pwd(passwordEncoder.encode("motdepasse"))
                .role(Role.USER)
                .build());

        autre = users.save(User.builder()
                .username("langue-voisin")
                .phone(44881133L)
                .pwd(passwordEncoder.encode("motdepasse"))
                .role(Role.USER)
                .build());
    }

    private String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    private String langueEnBase(User user) {
        return users.findById(user.getId()).orElseThrow().getPreferredLanguage();
    }

    @Test
    @DisplayName("un compte cree sans preference est en francais")
    void defautFrancais() {
        assertEquals("fr", langueEnBase(appelant),
                "la colonne porte son defaut: les comptes existants gardent le francais");
    }

    @Test
    @DisplayName("PUT /api/users/me/language enregistre la langue de l'appelant")
    @WithMockUser(username = "langue-test", roles = "USER")
    void changerSaLangue() throws Exception {
        mvc.perform(put("/api/users/me/language")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("language", "ar"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("ar"));

        assertEquals("ar", langueEnBase(appelant));
        assertEquals("fr", langueEnBase(autre),
                "changer sa langue ne doit toucher aucun autre compte");
    }

    @Test
    @DisplayName("les trois langues de l'application sont acceptees")
    @WithMockUser(username = "langue-test", roles = "USER")
    void troisLangues() throws Exception {
        for (String langue : new String[] { "fr", "ar", "en" }) {
            mvc.perform(put("/api/users/me/language")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("language", langue))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.language").value(langue));

            assertEquals(langue, langueEnBase(appelant));
        }
    }

    @Test
    @DisplayName("la casse et l'etiquette regionale envoyees par le mobile sont tolerees")
    @WithMockUser(username = "langue-test", roles = "USER")
    void etiquetteRegionaleToleree() throws Exception {
        mvc.perform(put("/api/users/me/language")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("language", "AR-MR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("ar"));

        assertEquals("ar", langueEnBase(appelant),
                "le code stocke est normalise, pour que la resolution ne depende pas du client");
    }

    @Test
    @DisplayName("une langue non supportee rend 400 et ne change rien")
    @WithMockUser(username = "langue-test", roles = "USER")
    void langueNonSupportee() throws Exception {
        mvc.perform(put("/api/users/me/language")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("language", "klingon"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        assertEquals("fr", langueEnBase(appelant));
    }

    @Test
    @DisplayName("un corps vide ou sans champ rend 400")
    @WithMockUser(username = "langue-test", roles = "USER")
    void corpsInvalide() throws Exception {
        mvc.perform(put("/api/users/me/language")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/users/me/language")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("language", ""))))
                .andExpect(status().isBadRequest());

        assertEquals("fr", langueEnBase(appelant));
    }

    @Test
    @DisplayName("sans authentification, la route est refusee avant le controleur")
    void sansJeton() throws Exception {
        // 403 et non 401: la route tombe sous anyRequest().authenticated(), et
        // la chaine de securite la refuse avant d'atteindre le controleur. Le
        // 401 que celui-ci sait rendre ne couvre que le cas ou le filtre a
        // laisse passer sans principal exploitable.
        mvc.perform(put("/api/users/me/language")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("language", "ar"))))
                .andExpect(status().isForbidden());

        assertEquals("fr", langueEnBase(appelant));
    }

    @Test
    @DisplayName("la langue choisie est relisible sur la fiche du compte")
    @WithMockUser(username = "langue-test", roles = "USER")
    void langueVisibleSurLaFiche() throws Exception {
        mvc.perform(put("/api/users/me/language")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("language", "en"))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/users/" + appelant.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("en"));
    }

    @Test
    @DisplayName("PATCH /api/users/{id} ne permet pas d'ecrire la langue")
    @WithMockUser(username = "langue-test", roles = "USER")
    void langueNonModifiableParLaRouteGenerique() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/users/" + appelant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("preferredLanguage", "ar"))))
                .andExpect(status().isOk());

        assertEquals("fr", langueEnBase(appelant),
                "le seul chemin d'ecriture doit rester PUT /api/users/me/language");
    }
}
