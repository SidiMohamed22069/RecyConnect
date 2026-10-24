package com.project.RecyConnect.Controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.android.sha256-fingerprints=aa:bb:cc, DD:EE:FF")
@AutoConfigureMockMvc
class WellKnownControllerTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("assetlinks.json est public, en JSON, avec le paquet et les empreintes en majuscules")
    void assetLinks() throws Exception {
        mvc.perform(get("/.well-known/assetlinks.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].relation[0]").value("delegate_permission/common.handle_all_urls"))
                .andExpect(jsonPath("$[0].target.package_name").value("com.recyconnect.app.neyan"))
                .andExpect(jsonPath("$[0].target.sha256_cert_fingerprints[0]").value("AA:BB:CC"))
                .andExpect(jsonPath("$[0].target.sha256_cert_fingerprints[1]").value("DD:EE:FF"));
    }
}
