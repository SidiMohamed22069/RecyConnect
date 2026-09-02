package com.project.RecyConnect.Model;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import java.time.OffsetDateTime;
import java.util.List;

@Entity
/*
 * L'index qui rend la carte tenable.
 *
 * <p>`findInBounds` filtre le statut puis borne latitude et longitude : sans
 * index, chaque deplacement de carte fait parcourir la table entiere. L'ordre
 * des colonnes suit celui de la requete — egalite d'abord, intervalles ensuite,
 * seule disposition qu'un index B-tree sait exploiter jusqu'au bout.
 *
 * <p>Declare ici plutot qu'en migration : `ddl-auto=update` cree les index
 * annotes, et le projet n'a pas d'outil de migration.
 */
@Table(indexes = {
        @Index(name = "idx_product_status_position",
                columnList = "status, latitude, longitude")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private OffsetDateTime createdAt;
    private String title;
    private String description;
    private Double price;
    private String unit;
    private Long quantityTotal;
    private Long quantityAvailable;

    @Convert(converter = ProductStatusConverter.class)
    private ProductStatus status;

    /**
     * La moughataa ou la marchandise est a charger.
     *
     * <p>Nulle pour les annonces publiees avant l'ajout du champ: elles
     * s'affichent alors sans badge de lieu plutot qu'avec un lieu invente, et
     * restent rendues par un filtre "toutes zones".
     */
    @Enumerated(EnumType.STRING)
    private Moughataa location;

    /**
     * Le point GPS du lot, tel que le vendeur l'a pose. Facultatif.
     *
     * <p>Toujours stocke exact. Ce qui en sort depend de {@link #geoPrecision}
     * et de qui regarde: voir la lecture dans {@code ProductService}. Stocker
     * l'arrondi aurait perdu l'information sans rien proteger de plus — le
     * vendeur, lui, doit revoir son propre point tel qu'il l'a place.
     *
     * <p>Nulles pour toute annonce publiee avant la carte: la moughataa reste
     * la seule information de lieu obligatoire.
     */
    private Double latitude;
    private Double longitude;

    /**
     * Ce que le vendeur accepte de montrer de ce point.
     *
     * <p>Nulle sur les annonces sans coordonnees. Lue comme
     * {@link GeoPrecision#APPROX} partout ailleurs: la valeur la plus prudente
     * est celle qu'on suppose quand on ne sait pas.
     */
    @Enumerated(EnumType.STRING)
    private GeoPrecision geoPrecision;
    
    // Lazy, mais chargee par lots: lister N annonces ne declenche pas N requetes.
    @ElementCollection
    @BatchSize(size = 50)
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url")
    private List<String> imageUrls;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "product")
    private List<Negotiation> negotiations;
}
