package com.project.RecyConnect.Model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProductStatus {
    AVAILABLE("available"),
    /**
     * Retiree du catalogue par son auteur, sans etre supprimee.
     *
     * <p>Le vendeur n'avait que deux issues: laisser l'annonce recevoir des
     * offres qu'il ne pouvait honorer, ou la supprimer — en perdant son
     * anciennete et ses offres. Une annonce en pause reste visible dans "mes
     * annonces" et invisible partout ailleurs.
     */
    PAUSED("paused"),
    PENDING("pending"),
    /** Ecoulee: quantite epuisee, ou close par son auteur. */
    RECYCLED("recycled"),
    ARCHIVED("archived");

    /**
     * Les statuts qu'un vendeur peut choisir lui-meme.
     *
     * <p>{@code PENDING} et {@code ARCHIVED} n'en sont pas: le premier est un
     * etat transitoire du serveur, le second le resultat d'une suppression.
     */
    public static final java.util.Set<ProductStatus> SELECTABLE_BY_OWNER =
            java.util.Set.of(AVAILABLE, PAUSED, RECYCLED);

    private final String value;

    ProductStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ProductStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        for (ProductStatus status : values()) {
            if (status.value.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }

        throw new IllegalArgumentException("Unknown product status: " + value);
    }
}