package com.project.RecyConnect.DTO;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * La fiche publique d'un vendeur.
 *
 * <p>Sur une place de marche entre inconnus qui se termine par un echange
 * physique de marchandise et d'argent, "a qui ai-je affaire ?" est la premiere
 * question de l'acheteur. Aucun ecran n'y repondait.
 *
 * <p>Ce DTO existe precisement pour ne pas ouvrir {@code /api/users/{id}}, qui
 * porte le numero de telephone et l'adresse d'un compte: rien ici n'est prive.
 * Le numero reste reserve aux deux parties d'une offre acceptee.
 */
@Data
public class PublicUserDTO {
    private Long id;
    private String username;
    private String imageData;

    /** Nulle pour les comptes anterieurs a l'ajout de la colonne. */
    private OffsetDateTime memberSince;

    private Integer publishedCount;
    private Integer recycledCount;

    /** Offres acceptees en tant que vendeur: les affaires reellement conclues. */
    private Long completedDeals;

    /**
     * Part des offres recues auxquelles le vendeur a repondu, en pourcentage.
     *
     * <p>Nul tant qu'il n'a recu aucune offre: un taux de 0 % afficherait un
     * mauvais eleve la ou il n'y a simplement rien a mesurer.
     */
    private Integer responseRate;

    private Double reviewAverage;
    private Long reviewCount;
}
