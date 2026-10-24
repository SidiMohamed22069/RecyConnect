package com.project.RecyConnect.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.RecyConnect.Model.PhoneVerification;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.PhoneVerificationRepository;
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

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nom et numero sont uniques, et un numero ne change qu'apres verification
 * par SMS depuis le compte lui-meme.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserProfileUpdateTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepo users;
    @Autowired PhoneVerificationRepository codes;
    @Autowired PasswordEncoder encoder;

    private User moi;
    private User admin;

    @BeforeEach
    void setUp() {
        moi = users.save(User.builder().username("profil-moi").phone(44330001L).pwd(encoder.encode("x")).role(Role.USER).build());
        admin = users.save(User.builder().username("admin").phone(44330002L).pwd(encoder.encode("x")).role(Role.ADMIN).build());
    }

    @Test
    @WithMockUser(username = "profil-moi", roles = "USER")
    @DisplayName("prendre le nom d'un autre compte est refuse en 409")
    void nomDejaPris() throws Exception {
        mvc.perform(put("/api/users/" + moi.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "admin"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
        assertEquals("profil-moi", users.findById(moi.getId()).orElseThrow().getUsername());
        assertEquals(Role.ADMIN, users.findByUsername("admin").getRole(), "le compte admin est intact");
    }

    @Test
    @WithMockUser(username = "profil-moi", roles = "USER")
    @DisplayName("changer de numero sans code verifie est refuse en 409")
    void numeroSansCode() throws Exception {
        mvc.perform(put("/api/users/" + moi.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("phone", 44339999L))))
                .andExpect(status().isConflict());
        assertEquals(44330001L, users.findById(moi.getId()).orElseThrow().getPhone());
    }

    @Test
    @WithMockUser(username = "profil-moi", roles = "USER")
    @DisplayName("le numero d'un autre compte est refuse meme avec un code")
    void numeroDejaPris() throws Exception {
        mvc.perform(put("/api/users/" + moi.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("phone", 44330002L))))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "profil-moi", roles = "USER")
    @DisplayName("send-code, verify-code (connecte) puis PUT : le numero change, le code est consomme")
    void numeroAvecCodeVerifie() throws Exception {
        // Le code tel que PhoneVerificationService l'aurait enregistre.
        codes.save(PhoneVerification.builder().phone(44339999L).code("123456")
                .createdAt(OffsetDateTime.now()).build());

        mvc.perform(post("/api/auth/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("phone", "44339999", "code", "123456"))))
                .andExpect(status().isOk());

        mvc.perform(put("/api/users/" + moi.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("phone", 44339999L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(44339999L));
        assertEquals(44339999L, users.findById(moi.getId()).orElseThrow().getPhone());

        // Un second changement sans nouveau code echoue : le code a ete consomme.
        mvc.perform(put("/api/users/" + moi.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("phone", 44330001L))))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("l'administration change un numero sans code SMS")
    void adminSansCode() throws Exception {
        mvc.perform(put("/api/users/" + moi.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("phone", 44338888L))))
                .andExpect(status().isOk());
        assertEquals(44338888L, users.findById(moi.getId()).orElseThrow().getPhone());
    }
}
