package com.project.RecyConnect.Config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les pages legales doivent etre lisibles sans compte.
 *
 * <p>Google Play exige que la politique de confidentialite et la page de
 * suppression de compte soient atteignables <em>sans installer l'application</em> :
 * ce sont les URL declarees dans "Contenu de l'application", et celles vers
 * lesquelles pointe {@code LegalLinks} cote mobile. Servies derriere
 * {@code anyRequest().authenticated()}, elles rendraient 401 au reviseur — un
 * motif de refus a lui seul.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LegalPagesPublicTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("la politique de confidentialite est servie sans jeton")
    void politiqueDeConfidentialitePublique() throws Exception {
        mvc.perform(get("/legal/privacy.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("RecyConnect")));
    }

    @Test
    @DisplayName("les conditions et la page de suppression aussi")
    void autresPagesPubliques() throws Exception {
        mvc.perform(get("/legal/terms.html")).andExpect(status().isOk());
        mvc.perform(get("/legal/delete-account.html")).andExpect(status().isOk());
        mvc.perform(get("/legal/index.html")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("ouvrir /legal n'ouvre pas l'API pour autant")
    void lApiResteFermee() throws Exception {
        mvc.perform(get("/api/users/1")).andExpect(status().is4xxClientError());
    }
}
