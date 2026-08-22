package com.project.RecyConnect.Service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verrouille la reecriture des URL de photos.
 *
 * Regression reelle: apres un changement d'adresse du serveur, les URL
 * absolues enregistrees en base pointaient encore vers l'ancien hote
 * (207.180.222.234) et plus aucune photo de produit ne s'affichait.
 */
class FileUrlServiceTest {

    private static final String HOST = "http://149.202.43.173";
    private static final String PORT = "8081";
    private static final String BASE = HOST + ":" + PORT;

    private FileUrlService service() {
        return new FileUrlService(HOST, PORT);
    }

    @Test
    @DisplayName("une URL enregistree avec l'ancien hote est ramenee sur le serveur courant")
    void rewritesLegacyHost() {
        assertEquals(BASE + "/api/files/photo.jpg",
                service().toPublicUrl("http://207.180.222.234:8081/api/files/photo.jpg"));
    }

    @Test
    @DisplayName("localhost, laisse par un upload local, est ramene sur le serveur courant")
    void rewritesLocalhost() {
        assertEquals(BASE + "/api/files/photo.jpg",
                service().toPublicUrl("http://localhost:8081/api/files/photo.jpg"));
    }

    @Test
    @DisplayName("un nom de fichier nu ou un chemin relatif devient une URL complete")
    void expandsRelativeValues() {
        FileUrlService service = service();
        assertEquals(BASE + "/api/files/photo.jpg", service.toPublicUrl("photo.jpg"));
        assertEquals(BASE + "/api/files/photo.jpg", service.toPublicUrl("/uploads/photo.jpg"));
    }

    @Test
    @DisplayName("une image hebergee ailleurs n'est pas touchee")
    void keepsExternalUrls() {
        FileUrlService service = service();
        String external = "https://cdn.example.com/images/photo.jpg";
        assertEquals(external, service.toPublicUrl(external));
        assertEquals("data:image/png;base64,AAAA", service.toPublicUrl("data:image/png;base64,AAAA"));
    }

    @Test
    @DisplayName("les valeurs vides sont rendues telles quelles")
    void keepsEmptyValues() {
        FileUrlService service = service();
        assertNull(service.toPublicUrl(null));
        assertEquals("", service.toPublicUrl(""));
        assertNull(service.toPublicUrls(null));
    }

    @Test
    @DisplayName("la liste complete est reecrite")
    void rewritesLists() {
        List<String> rewritten = service().toPublicUrls(Arrays.asList(
                "http://207.180.222.234:8081/api/files/a.jpg",
                "http://localhost:8081/api/files/b.jpg"));
        assertEquals(Arrays.asList(BASE + "/api/files/a.jpg", BASE + "/api/files/b.jpg"), rewritten);
    }

    @Test
    @DisplayName("le port n'est ajoute que s'il manque")
    void buildsBaseUrl() {
        // Port deja present dans app.server.url: ne pas le doubler.
        assertEquals("http://1.2.3.4:9000/api/files/a.jpg",
                new FileUrlService("http://1.2.3.4:9000", PORT).toPublicUrl("a.jpg"));
        // Reverse proxy HTTPS: le port interne n'a pas a apparaitre.
        assertEquals("https://api.recyconnect.mr/api/files/a.jpg",
                new FileUrlService("https://api.recyconnect.mr", PORT).toPublicUrl("a.jpg"));
        // Slash final ou schema absent: tolere.
        assertEquals(BASE + "/api/files/a.jpg",
                new FileUrlService(HOST + "/", PORT).toPublicUrl("a.jpg"));
        assertEquals(BASE + "/api/files/a.jpg",
                new FileUrlService("149.202.43.173", PORT).toPublicUrl("a.jpg"));
    }
}
