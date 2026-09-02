package com.project.RecyConnect.DTO;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class UserStatsDTO {
    private Long id;
    private OffsetDateTime createdAt;
    private Long userId;
    private Integer totalProducts;
    private Integer recycledCount;
    private Integer availableCount;
    private String recyclingRate;

    /**
     * Quantite reellement ecoulee, dans l'unite des annonces (kg pour
     * l'essentiel du catalogue).
     *
     * <p>C'est ce chiffre — et non le nombre d'annonces — qui se traduit en
     * une phrase parlante: "vous avez detourne 1,2 tonne de dechets de la
     * decharge".
     */
    private Long recycledQuantity;

    /** Date d'ouverture du compte, nulle pour les comptes anterieurs. */
    private java.time.OffsetDateTime memberSince;
}
