package com.project.RecyConnect.Model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * La moughataa ou se trouve la marchandise.
 *
 * <p>Sur une place de marche ou l'acheteur vient charger lui-meme, le lieu
 * decide de l'affaire avant le prix. Une premiere etape volontairement sans
 * carte ni GPS: un quartier declare suffit a repondre a "est-ce que je peux y
 * aller ?", sans demander de permission de localisation ni exposer l'adresse
 * d'un particulier.
 *
 * <p>Le code JSON est stable ({@code tevragh_zeina}) et sert de cle de
 * traduction cote mobile: le serveur ne rend jamais de libelle traduit.
 */
public enum Moughataa {
    TEVRAGH_ZEINA("tevragh_zeina", 18.0975, -15.9855),
    KSAR("ksar", 18.1006, -15.9633),
    SEBKHA("sebkha", 18.0787, -15.9900),
    DAR_NAIM("dar_naim", 18.1367, -15.9333),
    TOUJOUNINE("toujounine", 18.1064, -15.8983),
    ARAFAT("arafat", 18.0563, -15.9358),
    EL_MINA("el_mina", 18.0500, -15.9700),
    RIYAD("riyad", 18.0106, -15.8878),
    /**
     * Hors des huit moughataas de Nouakchott, ou non precise.
     *
     * <p>Sans centre: la zone designe precisement ce qui n'est dans aucune des
     * huit autres, souvent hors de la ville. Lui donner le centre de Nouakchott
     * inventerait un lieu.
     */
    AUTRE("autre", null, null);

    private final String value;

    /**
     * Le centre approximatif de la moughataa, ou {@code null}.
     *
     * <p>C'est ce qui permet a la carte d'exister des le premier jour: aucune
     * des annonces deja publiees ne porte de coordonnees, et attendre que les
     * vendeurs en ajoutent aurait donne une carte vide pendant des semaines.
     * Une annonce sans point s'affiche donc au centre de son quartier, annoncee
     * comme approximative — jamais presentee comme une adresse.
     *
     * <p>Les memes valeurs existent cote mobile ({@code core/utils/moughataa.dart}):
     * une annonce doit tomber au meme endroit, que la carte la place elle-meme
     * ou que le serveur l'ait fait.
     */
    private final Double centroidLatitude;
    private final Double centroidLongitude;

    Moughataa(String value, Double centroidLatitude, Double centroidLongitude) {
        this.value = value;
        this.centroidLatitude = centroidLatitude;
        this.centroidLongitude = centroidLongitude;
    }

    public Double getCentroidLatitude() {
        return centroidLatitude;
    }

    public Double getCentroidLongitude() {
        return centroidLongitude;
    }

    public boolean hasCentroid() {
        return centroidLatitude != null && centroidLongitude != null;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Moughataa fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (Moughataa m : values()) {
            if (m.value.equalsIgnoreCase(value) || m.name().equalsIgnoreCase(value)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown moughataa: " + value);
    }

    /**
     * Comme {@link #fromValue(String)}, mais rend {@code null} plutot que de
     * lever pour une valeur inconnue.
     *
     * <p>Sert aux parametres de recherche: un filtre mal orthographie doit
     * rendre le catalogue entier, pas une erreur 500.
     */
    public static Moughataa parseOrNull(String value) {
        try {
            return fromValue(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
