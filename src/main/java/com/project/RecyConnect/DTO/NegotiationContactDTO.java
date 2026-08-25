package com.project.RecyConnect.DTO;

import lombok.Data;

/**
 * La mise en relation des deux parties d'une offre acceptee.
 *
 * <p>Ce que l'application demandait jusqu'ici a {@code GET /api/users/{id}} :
 * elle lisait la fiche complete des deux comptes pour n'en garder que le
 * numero. Tout compte authentifie pouvait ainsi parcourir l'annuaire — le
 * point C3 de l'audit. Cette reponse-ci ne porte que ce que la transaction
 * justifie : deux identifiants, deux noms, deux numeros, et rien du reste du
 * profil.
 */
@Data
public class NegotiationContactDTO {

    private Long negotiationId;

    /** Statut de l'offre au moment de la lecture — toujours {@code accepted}. */
    private String status;

    /** Celui qui a fait l'offre ({@code sender} cote modele). */
    private Long buyerId;
    private String buyerUsername;
    private String buyerPhone;

    /** Le proprietaire de l'annonce ({@code receiver} cote modele). */
    private Long sellerId;
    private String sellerUsername;
    private String sellerPhone;
}
