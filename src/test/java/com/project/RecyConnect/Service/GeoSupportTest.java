package com.project.RecyConnect.Service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L'arithmetique sur laquelle reposent la carte, le classement par distance et
 * l'arrondi qui protege l'adresse d'un vendeur particulier.
 *
 * <p>La meme existe cote mobile ({@code core/utils/geo.dart}) : les deux
 * doivent rester d'accord, sans quoi une annonce se dessinerait a deux endroits
 * selon qui la place.
 */
class GeoSupportTest {

    @Test
    @DisplayName("deux fois le meme point: zero")
    void memePoint() {
        assertEquals(0.0, GeoSupport.distanceKm(18.09, -15.98, 18.09, -15.98), 0.0001);
    }

    @Test
    @DisplayName("un degre de latitude vaut environ 111 km")
    void degreDeLatitude() {
        assertEquals(111.2, GeoSupport.distanceKm(18.0, -16.0, 19.0, -16.0), 0.5);
    }

    @Test
    @DisplayName("la distance est symetrique")
    void symetrie() {
        double aller = GeoSupport.distanceKm(18.09, -15.98, 18.13, -15.93);
        double retour = GeoSupport.distanceKm(18.13, -15.93, 18.09, -15.98);
        assertEquals(aller, retour, 0.0001);
    }

    @Test
    @DisplayName("deux moughataas voisines sont a quelques kilometres")
    void moughataasVoisines() {
        // Tevragh Zeina et Ksar: de l'ordre de deux kilometres, pas deux cents.
        double km = GeoSupport.distanceKm(18.0975, -15.9855, 18.1006, -15.9633);
        assertTrue(km < 5, "distance inattendue: " + km);
        assertTrue(km > 0.5, "distance inattendue: " + km);
    }

    @Test
    @DisplayName("l'arrondi rend un autre point que l'exact")
    void arrondiDeplace() {
        double[] flou = GeoSupport.blur(18.0912345, -15.9812345);
        assertNotEquals(18.0912345, flou[0]);
        assertNotEquals(-15.9812345, flou[1]);
    }

    @Test
    @DisplayName("il reste dans le voisinage: moins de 250 m")
    void arrondiProche() {
        double[] flou = GeoSupport.blur(18.0912345, -15.9812345);
        double ecart = GeoSupport.distanceKm(18.0912345, -15.9812345, flou[0], flou[1]);
        assertTrue(ecart < 0.25, "ecart inattendu: " + ecart);
    }

    @Test
    @DisplayName("deux voisins d'une meme case rendent exactement le meme point")
    void memeCaseMemePoint() {
        // C'est toute la protection: sans cette egalite stricte, l'ecart
        // residuel entre deux arrondis suffit a remonter vers la position
        // d'origine.
        assertArrayEquals(
                GeoSupport.blur(18.09120, -15.98120),
                GeoSupport.blur(18.09125, -15.98125));
    }

    @Test
    @DisplayName("deux points eloignes ne se confondent pas")
    void casesDistinctes() {
        assertNotEquals(
                GeoSupport.blur(18.0912, -15.9812)[0],
                GeoSupport.blur(18.1312, -15.9312)[0]);
    }

    @Test
    @DisplayName("arrondir un point deja arrondi ne le deplace plus")
    void idempotent() {
        double[] une = GeoSupport.blur(18.0912345, -15.9812345);
        assertArrayEquals(une, GeoSupport.blur(une[0], une[1]));
    }

    @Test
    @DisplayName("un couple absent, aberrant ou (0,0) n'est pas une position")
    void validite() {
        assertFalse(GeoSupport.isValid(null, -15.98));
        assertFalse(GeoSupport.isValid(18.09, null));
        assertFalse(GeoSupport.isValid(0.0, 0.0));
        assertFalse(GeoSupport.isValid(200.0, 10.0));
        assertFalse(GeoSupport.isValid(Double.NaN, 10.0));
        assertTrue(GeoSupport.isValid(18.09, -15.98));
    }

    @Test
    @DisplayName("le rectangle deduit d'un rayon couvre bien ce rayon")
    void rectangleDUnRayon() {
        double lat = 18.09;
        double span = GeoSupport.latitudeDegreesFor(10.0);
        // Le bord du rectangle est a dix kilometres, pas a huit ni a douze:
        // c'est ce qui garantit que le filtre exact ne perde personne.
        assertEquals(10.0, GeoSupport.distanceKm(lat, -15.98, lat + span, -15.98), 0.2);

        double lngSpan = GeoSupport.longitudeDegreesFor(10.0, lat);
        assertEquals(10.0, GeoSupport.distanceKm(lat, -15.98, lat, -15.98 + lngSpan), 0.2);
    }
}
