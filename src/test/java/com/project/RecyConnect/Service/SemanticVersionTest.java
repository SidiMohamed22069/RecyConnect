package com.project.RecyConnect.Service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticVersionTest {

    private static int compare(String a, String b) {
        SemanticVersion left = SemanticVersion.parseOrNull(a);
        SemanticVersion right = SemanticVersion.parseOrNull(b);
        assertNotNull(left, a);
        assertNotNull(right, b);
        return left.compareTo(right);
    }

    @Test
    @DisplayName("les composants se comparent comme des nombres, pas comme du texte")
    void comparesNumerically() {
        assertTrue(compare("1.2.0", "1.3.0") < 0);
        // Le piege classique: "1.10.0".compareTo("1.3.0") est negatif.
        assertTrue(compare("1.10.0", "1.3.0") > 0);
        assertTrue(compare("1.2.5", "1.2.0") > 0);
        assertEquals(0, compare("1.2.0", "1.2.0"));
        assertTrue(compare("2.0.0", "1.99.99") > 0);
    }

    @Test
    @DisplayName("les formes tolerees sont normalisees")
    void normalizes() {
        assertEquals("1.2.0", SemanticVersion.parseOrNull("1.2").toString());
        assertEquals("1.0.0", SemanticVersion.parseOrNull("v1").toString());
        assertEquals("1.2.3", SemanticVersion.parseOrNull(" 1.2.3 ").toString());
        // Les metadonnees de build n'entrent pas dans la comparaison.
        assertEquals(0, compare("1.2.3+42", "1.2.3+7"));
    }

    @Test
    @DisplayName("une pre-publication precede la version stable de meme numero")
    void preReleaseOrdering() {
        assertTrue(compare("1.3.0-beta", "1.3.0") < 0);
        assertTrue(compare("1.3.0-alpha", "1.3.0-beta") < 0);
        assertTrue(compare("1.3.0-beta.2", "1.3.0-beta.10") < 0);
        assertTrue(compare("1.3.0-beta", "1.3.0-beta.1") < 0);
    }

    @Test
    @DisplayName("ce qui n'est pas une version rend null, sans exception")
    void rejectsGarbage() {
        assertNull(SemanticVersion.parseOrNull(null));
        assertNull(SemanticVersion.parseOrNull(""));
        assertNull(SemanticVersion.parseOrNull("latest"));
        assertNull(SemanticVersion.parseOrNull("1.2..0"));
        assertNull(SemanticVersion.parseOrNull("1.2.3.4"));
        assertNull(SemanticVersion.parseOrNull("-1.0.0"));
        assertNull(SemanticVersion.parseOrNull("1.2.3-"));
    }
}
