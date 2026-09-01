package com.project.RecyConnect.Model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Un tour de negociation: qui a propose quoi, et quand.
 *
 * <p>Une negociation reelle ne se resume pas a accepter ou refuser — la
 * troisieme reponse, "pas a ce prix-la, mais a celui-ci", est la plus
 * frequente. Sans trace, une contre-proposition ecrasait l'offre precedente et
 * les deux parties perdaient le fil ("25 -> 20 -> 22").
 */
@Entity
@Table(name = "negotiation_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NegotiationHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private OffsetDateTime createdAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "negotiation_id")
    private Negotiation negotiation;

    /** L'auteur du tour: l'acheteur au depart, le vendeur s'il contre-propose. */
    @ManyToOne
    @JoinColumn(name = "author_id")
    private User author;

    /** {@code OFFER}, {@code COUNTER_OFFER} ou {@code UPDATED}. */
    private String kind;

    private Double price;
    private Integer quantity;

    @PrePersist
    void stampCreatedAt() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
