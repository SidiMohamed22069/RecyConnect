package com.project.RecyConnect.Controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Model.UserSession;
import com.project.RecyConnect.Repository.UserRepo;
import com.project.RecyConnect.Repository.UserSessionRepository;
import com.project.RecyConnect.Service.RefreshTokenService;
import com.project.RecyConnect.Service.UserSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/auth/refresh} vu du reseau, contexte Spring complet.
 *
 * <p>Les tests unitaires de {@code RefreshTokenService} verifient la regle
 * metier. Ce qui se joue ici est le cablage, invisible d'un mock: le point
 * d'entree est-il bien atteignable sans jeton d'acces (il ne peut pas en
 * exiger un, celui de l'appelant vient d'expirer), le JSON porte-t-il les noms
 * de champs que l'application lit, et surtout le jeton rendu ouvre-t-il
 * reellement une requete authentifiee — c'est-a-dire passe-t-il
 * {@code JwtRequestFilter}, qui recoupe sessionVersion et deviceId.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthRefreshEndpointTest {

    private static final String DEVICE = "appareil-de-test";
    private static final Long PHONE = 44556677L;

    @Autowired private MockMvc mvc;
    @Autowired private UserRepo userRepo;
    @Autowired private UserSessionRepository sessionRepository;
    @Autowired private UserSessionService userSessionService;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private ObjectMapper objectMapper;

    private User user;
    private String refreshToken;

    @BeforeEach
    void setUp() {
        User existant = userRepo.findByPhone(PHONE);
        if (existant != null) {
            sessionRepository.deleteById(existant.getId());
            userRepo.delete(existant);
        }

        user = userRepo.save(User.builder()
                .username("refresh-" + PHONE)
                .phone(PHONE)
                .pwd("$2a$10$abcdefghijklmnopqrstuv")
                .role(Role.USER)
                .build());

        userSessionService.replaceSession(user.getId(), DEVICE, "Telephone de test", "fcm-test");
        refreshToken = refreshTokenService.issueFor(user.getId());
        assertNotNull(refreshToken, "la session vient d'etre ouverte, un jeton doit etre emis");
    }

    private String corps(String jeton) {
        return "{\"refreshToken\":\"" + jeton + "\"}";
    }

    @Test
    @DisplayName("le renouvellement rend un jeton d'acces qui ouvre vraiment l'API")
    void leJetonRenduEstUtilisable() throws Exception {
        String reponse = mvc.perform(post("/api/auth/refresh")
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(reponse);
        assertNotEquals(refreshToken, json.get("refreshToken").asText(),
                "le jeton doit tourner a chaque usage");

        // La vraie preuve: le jeton rendu franchit JwtRequestFilter, qui
        // recoupe sessionVersion et deviceId avec la session en base.
        mvc.perform(get("/api/users/" + user.getId())
                        .header("Authorization", "Bearer " + json.get("token").asText())
                        .header("X-Device-Id", DEVICE))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("aucun jeton d'acces n'est exige pour renouveler")
    void aucunJetonDAccesNestExige() throws Exception {
        // Le principe meme du point d'entree: celui de l'appelant vient
        // d'expirer. L'exiger rendrait le mecanisme inutilisable.
        mvc.perform(post("/api/auth/refresh")
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps(refreshToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("le jeton presente ne resservira pas")
    void leJetonNeSertQuUneFois() throws Exception {
        mvc.perform(post("/api/auth/refresh")
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps(refreshToken)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/refresh")
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un autre appareil est refuse, meme avec le bon jeton")
    void unAutreAppareilEstRefuse() throws Exception {
        mvc.perform(post("/api/auth/refresh")
                        .header("X-Device-Id", "appareil-vole")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps(refreshToken)))
                .andExpect(status().isUnauthorized());

        // Sans en-tete du tout non plus.
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("se connecter ailleurs invalide le jeton de l'appareil precedent")
    void seConnecterAilleursInvalideLeJeton() throws Exception {
        userSessionService.replaceSession(user.getId(), "appareil-b", "Telephone B", "fcm-b");

        // La deconnexion a distance serait illusoire si l'ancien appareil
        // pouvait se refabriquer un jeton d'acces sans mot de passe.
        mvc.perform(post("/api/auth/refresh")
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un corps sans refreshToken est un defaut d'appelant, pas une session morte")
    void unCorpsVideRend400() throws Exception {
        mvc.perform(post("/api/auth/refresh")
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("les reponses qui n'emettent pas de jeton n'en montrent pas")
    void pasDeChampInutileAilleurs() throws Exception {
        UserSession session = sessionRepository.findById(user.getId()).orElseThrow();
        String acces = mvc.perform(post("/api/auth/refresh")
                        .header("X-Device-Id", session.getDeviceId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps(refreshToken)))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(acces).get("token").asText();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }
}
