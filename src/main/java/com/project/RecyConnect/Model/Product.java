package com.project.RecyConnect.Model;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import java.time.OffsetDateTime;
import java.util.List;

@Entity
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
