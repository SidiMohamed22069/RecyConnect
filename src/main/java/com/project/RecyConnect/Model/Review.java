package com.project.RecyConnect.Model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * L'avis laisse par un acheteur sur un vendeur, apres une offre acceptee.
 *
 * <p>Rien ne distinguait jusqu'ici un vendeur fiable d'un vendeur qui ne
 * repond jamais au telephone. C'est le mecanisme le moins couteux pour faire
 * baisser le taux d'echec des rendez-vous.
 *
 * <p>Le sens est volontairement unique — acheteur vers vendeur. La reciprocite
 * serait plus juste, mais elle dissuade les avis negatifs: un acheteur qui
 * sait qu'il sera note en retour hesite a signaler un rendez-vous manque.
 *
 * <p>Une seule note par negociation: c'est la transaction qui donne le droit
 * d'ecrire, et elle ne le donne qu'une fois.
 */
@Entity
@Table(
        name = "reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_review_negotiation",
                columnNames = {"negotiation_id"}
        )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private OffsetDateTime createdAt;

    /** La transaction qui ouvre le droit d'ecrire. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "negotiation_id")
    private Negotiation negotiation;

    /** L'acheteur, auteur de l'avis. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    /** Le vendeur, sujet de l'avis. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "target_id")
    private User target;

    /** De 1 a 5. */
    private Integer rating;

    /** Commentaire libre, facultatif. */
    @Column(length = 1000)
    private String comment;

    @PrePersist
    void stampCreatedAt() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
