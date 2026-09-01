package com.project.RecyConnect.DTO;

import com.project.RecyConnect.Model.Moughataa;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * Les criteres d'une recherche d'annonces.
 *
 * <p>Regroupes dans un objet plutot qu'etales en douze parametres: la
 * signature de {@code search} devenait illisible et une inversion entre deux
 * {@code Long} voisins n'aurait pas ete rattrapee par le compilateur.
 *
 * <p>Tout est facultatif. Un critere nul ne filtre pas.
 */
@Data
@Builder
public class ProductSearchCriteria {

    /** Mot recherche dans le titre et la description. */
    private String query;

    private Long categoryId;

    /** Le compte dont on veut ecarter les annonces — l'appelant, en pratique. */
    private Long excludeUserId;

    private Double minPrice;
    private Double maxPrice;
    private Long minQuantity;
    private String unit;
    private Moughataa location;

    /** Comptes bloques, dans un sens ou dans l'autre. */
    private Set<Long> hiddenUserIds;

    /** {@code recent} (defaut), {@code price_asc}, {@code price_desc}, {@code quantity_desc}, {@code oldest}. */
    private String sort;

    /** Page demandee, a partir de 0. Nulle: pas de pagination. */
    private Integer page;

    /** Taille de page. Nulle: le catalogue entier. */
    private Integer size;
}
