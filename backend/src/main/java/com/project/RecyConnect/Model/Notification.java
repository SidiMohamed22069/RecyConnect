package com.project.RecyConnect.Model;


import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private OffsetDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "sender_id")
    private User sender;

    @ManyToOne
    @JoinColumn(name = "receiver_id")
    private User receiver;

    @Column(columnDefinition = "text")
    private String message;

    private String title;
    
    private String type; // "OFFER_RECEIVED", "OFFER_REFUSED", "BROADCAST", etc.
    
    private Long relatedId; // ID de la négociation/produit concerné
    
    private Boolean isRead = false; // Pour marquer comme lue
}
