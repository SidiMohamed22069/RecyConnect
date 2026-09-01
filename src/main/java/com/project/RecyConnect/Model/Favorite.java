package com.project.RecyConnect.Model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Une annonce mise de cote par un utilisateur.
 *
 * <p>Sans favoris, un acheteur qui reperait trois lots interessants n'avait
 * aucun moyen d'y revenir: la recherche ne garantissait pas de retrouver
 * l'annonce vue la veille.
 *
 * <p>La contrainte d'unicite porte le "une seule fois" au niveau de la base:
 * deux appuis rapproches sur le coeur ne creent pas deux lignes.
 */
@Entity
@Table(
        name = "favorites",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_favorite_user_product",
                columnNames = {"user_id", "product_id"}
        )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Favorite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private OffsetDateTime createdAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @PrePersist
    void stampCreatedAt() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
