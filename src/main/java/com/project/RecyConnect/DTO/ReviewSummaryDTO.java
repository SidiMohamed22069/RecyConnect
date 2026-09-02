package com.project.RecyConnect.DTO;

import lombok.Data;

/**
 * La note moyenne d'un vendeur et le nombre d'avis qui la composent.
 *
 * <p>Le compte accompagne toujours la moyenne: 5,0 sur un seul avis et 4,6 sur
 * douze ne disent pas la meme chose, et l'afficher seul induirait en erreur.
 */
@Data
public class ReviewSummaryDTO {
    private Long userId;
    private Double average;
    private Long count;
}
