package com.project.RecyConnect.Model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Ce qu'un vendeur accepte de rendre public de la position de son lot.
 *
 * <p>Le vendeur est souvent un particulier, et la marchandise est chez lui:
 * publier son point GPS exact revient a publier son adresse, sur une
 * application que n'importe qui telecharge. Le choix lui appartient donc, et il
 * est demande au moment ou il sait ce qu'il publie et chez qui.
 *
 * <p>La valeur ne decrit pas la qualite du releve GPS mais une intention. Le
 * serveur stocke toujours le point exact; c'est a la lecture qu'il decide quoi
 * en montrer, selon qui regarde ({@code ProductService}).
 */
public enum GeoPrecision {
    /** Le point exact: une cour, un atelier, un entrepot qui veut etre trouve. */
    EXACT("exact"),

    /**
     * Une zone d'environ 300 m, la valeur par defaut.
     *
     * <p>Assez pour savoir dans quelle rue chercher, trop peu pour designer une
     * maison. Le point exact n'est alors revele qu'au vendeur lui-meme et a
     * l'acheteur dont l'offre a ete acceptee — au moment ou les deux ont deja
     * echange leurs numeros.
     */
    APPROX("approx");

    private final String value;

    GeoPrecision(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Lit une valeur recue, en retombant sur la plus prudente.
     *
     * <p>Ni {@code null} ni une valeur inconnue ne doivent produire "exact":
     * une version de l'application qui n'enverrait pas le champ exposerait
     * alors des adresses que personne n'a accepte de publier.
     */
    @JsonCreator
    public static GeoPrecision fromValue(String value) {
        if (value == null || value.isBlank()) {
            return APPROX;
        }
        for (GeoPrecision p : values()) {
            if (p.value.equalsIgnoreCase(value) || p.name().equalsIgnoreCase(value)) {
                return p;
            }
        }
        return APPROX;
    }
}
