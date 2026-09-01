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
    TEVRAGH_ZEINA("tevragh_zeina"),
    KSAR("ksar"),
    SEBKHA("sebkha"),
    DAR_NAIM("dar_naim"),
    TOUJOUNINE("toujounine"),
    ARAFAT("arafat"),
    EL_MINA("el_mina"),
    RIYAD("riyad"),
    /** Hors des huit moughataas de Nouakchott, ou non precise. */
    AUTRE("autre");

    private final String value;

    Moughataa(String value) {
        this.value = value;
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
