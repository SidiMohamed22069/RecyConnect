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
