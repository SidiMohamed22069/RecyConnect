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

    /**
     * Le centre d'une recherche par rayon, et son rayon en kilometres.
     *
     * <p>Les trois vont ensemble : sans centre, un rayon ne veut rien dire, et
     * le critere est alors ignore plutot que de vider le catalogue.
     *
     * <p>Le filtre accepte le centre de la moughataa comme point de repli pour
     * les annonces qui n'ont pas de coordonnees : il ordonne et retranche, il
     * n'affirme aucune distance a l'utilisateur. Sans ce repli, un rayon de
     * 25 km ferait disparaitre presque tout le catalogue, dont les annonces
     * n'ont pour la plupart pas encore de point.
     */
    private Double centerLatitude;
    private Double centerLongitude;
    private Double maxDistanceKm;

    /** Comptes bloques, dans un sens ou dans l'autre. */
    private Set<Long> hiddenUserIds;

    /**
     * {@code recent} (defaut), {@code price_asc}, {@code price_desc},
     * {@code quantity_desc}, {@code oldest}, {@code distance}.
     *
     * <p>{@code distance} suppose un centre ; sans lui, il retombe sur
     * {@code recent}.
     */
    private String sort;

    /** Page demandee, a partir de 0. Nulle: pas de pagination. */
    private Integer page;

    /** Taille de page. Nulle: le catalogue entier. */
    private Integer size;
}
