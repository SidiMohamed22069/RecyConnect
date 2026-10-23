package com.project.RecyConnect.Config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La force brute sur un mot de passe ou un code SMS bute sur 429. Le contexte
 * est propre a ce test (propriete surchargee) : la limitation est coupee pour
 * les autres, qui enchainent les requetes depuis la meme adresse.
 */
@SpringBootTest(properties = "app.rate-limit.enabled=true")
@AutoConfigureMockMvc
class RateLimitFilterTest {

    @Autowired MockMvc mvc;
    @Autowired RateLimiter limiter;

    @Test
    @DisplayName("dix essais de connexion sur un meme numero, puis 429")
    void loginParNumero() throws Exception {
        limiter.reset();
        String body = "{\"phone\":44550001,\"password\":\"faux\",\"deviceId\":\"d\",\"fcmToken\":\"t\"}";
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("vingt essais depuis une meme adresse, numeros differents, puis 429")
    void loginParAdresse() throws Exception {
        limiter.reset();
        for (int i = 0; i < 20; i++) {
            String body = "{\"phone\":4456" + String.format("%04d", i) + ",\"password\":\"faux\",\"deviceId\":\"d\",\"fcmToken\":\"t\"}";
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":44579999,\"password\":\"faux\",\"deviceId\":\"d\",\"fcmToken\":\"t\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("cinq codes essayes sur un numero, puis 429 : un code a six chiffres ne se devine pas")
    void verifyCodeParNumero() throws Exception {
        limiter.reset();
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/verify-code").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"44580001\",\"code\":\"00000" + i + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/verify-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"44580001\",\"code\":\"000009\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void fenetreGlissante() {
        RateLimiter l = new RateLimiter(true);
        assertTrue(l.tryAcquire("k", 2, Duration.ofMinutes(1)));
        assertTrue(l.tryAcquire("k", 2, Duration.ofMinutes(1)));
        assertFalse(l.tryAcquire("k", 2, Duration.ofMinutes(1)));
        assertTrue(l.tryAcquire("autre", 2, Duration.ofMinutes(1)));
        assertTrue(new RateLimiter(false).tryAcquire("k", 0, Duration.ofMinutes(1)), "desactive = jamais bloquant");
    }
}
