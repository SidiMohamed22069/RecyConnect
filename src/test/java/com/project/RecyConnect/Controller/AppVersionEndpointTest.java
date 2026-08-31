package com.project.RecyConnect.Controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/app/version} vu du reseau.
 *
 * <p>Ce qui se joue ici est le cablage, invisible d'un test unitaire: le point
 * d'entree doit repondre <em>sans jeton</em>. Derriere une authentification,
 * une version devenue incompatible avec le contrat de l'API — celle qui ne sait
 * plus se connecter — serait justement celle qui ne pourrait pas apprendre
 * qu'elle doit se mettre a jour.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.version.latest=1.3.0",
        "app.version.minimum=1.2.0",
        "app.version.android-url=https://play.google.com/store/apps/details?id=com.recyconnect.app.neyan",
        "app.version.ios-url=https://apps.apple.com/app/id123456789",
})
class AppVersionEndpointTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("la politique de version est lisible sans authentification")
    void servesPolicyAnonymously() throws Exception {
        mvc.perform(get("/api/app/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestVersion").value("1.3.0"))
                .andExpect(jsonPath("$.minimumVersion").value("1.2.0"))
                .andExpect(jsonPath("$.androidUrl")
                        .value("https://play.google.com/store/apps/details?id=com.recyconnect.app.neyan"))
                .andExpect(jsonPath("$.iosUrl").value("https://apps.apple.com/app/id123456789"));
    }
}
