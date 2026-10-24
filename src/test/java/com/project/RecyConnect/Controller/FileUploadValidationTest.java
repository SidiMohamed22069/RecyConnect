package com.project.RecyConnect.Controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seules des images entrent, reconnues par leur signature ; ce qui sort est
 * type d'apres l'extension enregistree et jamais "sniffe" par le navigateur.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FileUploadValidationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    @Autowired MockMvc mvc;

    @Test
    @WithMockUser(username = "upl", roles = "USER")
    @DisplayName("un fichier HTML deguise en .jpg est refuse en 415")
    void htmlRefuse() throws Exception {
        MockMultipartFile html = new MockMultipartFile("file", "photo.jpg", "image/jpeg",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/files/upload").file(html))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = "upl", roles = "USER")
    @DisplayName("un PNG nomme .html est enregistre en .png et servi en image/png avec nosniff")
    void pngAccepteEtServi() throws Exception {
        MockMultipartFile png = new MockMultipartFile("file", "page.html", "text/html", PNG);
        MvcResult result = mvc.perform(multipart("/api/files/upload").file(png))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        String filename = body.replaceAll(".*\"filename\":\"([^\"]+)\".*", "$1");
        assertTrue(filename.endsWith(".png"), filename);

        mvc.perform(get("/api/files/" + filename))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @WithMockUser(username = "upl", roles = "USER")
    @DisplayName("la suppression directe d'un fichier est reservee a l'administration")
    void suppressionReserveeAdmin() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/files/inexistant.png"))
                .andExpect(status().isForbidden());
    }

    @Test
    void signatures() {
        assertEquals("png", FileController.detectImageExtension(PNG));
        assertEquals("jpg", FileController.detectImageExtension(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}));
        assertEquals("gif", FileController.detectImageExtension("GIF89a".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("webp", FileController.detectImageExtension("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII)));
        assertNull(FileController.detectImageExtension("<svg xmlns".getBytes(StandardCharsets.US_ASCII)));
        assertNull(FileController.detectImageExtension(new byte[0]));
    }
}
