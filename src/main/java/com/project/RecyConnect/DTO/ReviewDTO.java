package com.project.RecyConnect.DTO;

import lombok.Data;

import java.time.OffsetDateTime;

/** Un avis laisse par un acheteur sur un vendeur. */
@Data
public class ReviewDTO {
    private Long id;
    private OffsetDateTime createdAt;
    private Long negotiationId;
    private Long authorId;
    private String authorUsername;
    private Long targetId;
    private Integer rating;
    private String comment;

    /** Le lot concerne, pour situer l'avis. */
    private String productTitle;
}
