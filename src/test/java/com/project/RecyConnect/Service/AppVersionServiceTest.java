package com.project.RecyConnect.Service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.project.RecyConnect.DTO.AppVersionDTO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La politique de version est la seule configuration de l'API capable de rendre
 * l'application inutilisable pour tout le parc installe. Ces tests verrouillent
 * les garde-fous qui l'empechent: une valeur illisible ou incoherente doit etre
 * neutralisee, jamais transmise telle quelle.
 */
class AppVersionServiceTest {

    private static final String PLAY = "https://play.google.com/store/apps/details?id=com.recyconnect.app.neyan";
    private static final String APPSTORE = "https://apps.apple.com/app/id123456789";

    @Test
    @DisplayName("une configuration valide est publiee telle quelle")
    void publishesValidPolicy() {
        AppVersionDTO policy = AppVersionService.buildPolicy("1.3.0", "1.2.0", PLAY, APPSTORE);

        assertEquals("1.3.0", policy.getLatestVersion());
        assertEquals("1.2.0", policy.getMinimumVersion());
        assertEquals(PLAY, policy.getAndroidUrl());
        assertEquals(APPSTORE, policy.getIosUrl());
    }

    @Test
    @DisplayName("une configuration vide ne publie aucune contrainte")
    void emptyConfigurationBlocksNobody() {
        AppVersionDTO policy = AppVersionService.buildPolicy("", "  ", "", null);

        assertNull(policy.getLatestVersion());
        assertNull(policy.getMinimumVersion());
        assertNull(policy.getAndroidUrl());
        assertNull(policy.getIosUrl());
    }

    @Test
    @DisplayName("un minimum illisible est omis plutot que transmis: une faute de frappe ne bloque personne")
    void malformedMinimumIsDropped() {
        AppVersionDTO policy = AppVersionService.buildPolicy("1.3.0", "1.2..0", PLAY, null);

        assertNull(policy.getMinimumVersion());
        assertEquals("1.3.0", policy.getLatestVersion());
    }

    @Test
    @DisplayName("un minimum superieur a la derniere version publiee est ramene a celle-ci")
    void minimumIsClampedToLatest() {
        // Exiger 2.0.0 quand le magasin sert 1.3.0 enfermerait l'utilisateur
        // devant un bouton "Mettre a jour" sans effet.
        AppVersionDTO policy = AppVersionService.buildPolicy("1.3.0", "2.0.0", PLAY, null);

        assertEquals("1.3.0", policy.getMinimumVersion());
        assertEquals("1.3.0", policy.getLatestVersion());
    }

    @Test
    @DisplayName("1.10.0 n'est pas vu comme anterieur a 1.3.0")
    void comparesNumericallyNotLexicographically() {
        // La comparaison de chaines ferait de 1.10.0 un minimum trop haut, donc
        // ramene a 1.3.0 par la regle precedente.
        AppVersionDTO policy = AppVersionService.buildPolicy("1.10.0", "1.3.0", PLAY, null);

        assertEquals("1.3.0", policy.getMinimumVersion());
        assertEquals("1.10.0", policy.getLatestVersion());
    }

    @Test
    @DisplayName("une URL de magasin qui n'est pas en https est omise")
    void rejectsNonHttpsStoreUrl() {
        AppVersionDTO policy = AppVersionService.buildPolicy(
                "1.3.0", "1.2.0", "market://details?id=com.recyconnect.app.neyan", "javascript:alert(1)");

        assertNull(policy.getAndroidUrl());
        assertNull(policy.getIosUrl());
    }

    @Test
    @DisplayName("une URL relative ou sans hote est omise")
    void rejectsUrlWithoutHost() {
        AppVersionDTO policy = AppVersionService.buildPolicy("1.3.0", "1.2.0", "/store/apps", "https://");

        assertNull(policy.getAndroidUrl());
        assertNull(policy.getIosUrl());
    }

    @Test
    @DisplayName("les versions sont normalisees: 1.2 vaut 1.2.0, le prefixe v est accepte")
    void normalizesVersions() {
        AppVersionDTO policy = AppVersionService.buildPolicy("v1.3", "1.2", PLAY, null);

        assertEquals("1.3.0", policy.getLatestVersion());
        assertEquals("1.2.0", policy.getMinimumVersion());
    }

    @Test
    @DisplayName("une pre-publication precede la version stable de meme numero")
    void preReleasePrecedesStable() {
        // 1.3.0-beta.1 comme minimum face a 1.3.0 : coherent, donc conserve.
        AppVersionDTO policy = AppVersionService.buildPolicy("1.3.0", "1.3.0-beta.1", PLAY, null);

        assertEquals("1.3.0-beta.1", policy.getMinimumVersion());
        assertTrue(SemanticVersion.parseOrNull("1.3.0-beta.1")
                .compareTo(SemanticVersion.parseOrNull("1.3.0")) < 0);
    }
}
