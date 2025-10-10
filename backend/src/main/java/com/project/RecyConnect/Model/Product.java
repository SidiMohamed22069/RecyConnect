package com.project.RecyConnect.Model;


import jakarta.persistence.*;
import lombok.*;
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
    private String status; // mappe ton status_enum; tu peux remplacer par enum Java si tu veux
    private String imageUrl;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "product")
    private List<Negotiation> negotiations;
}
