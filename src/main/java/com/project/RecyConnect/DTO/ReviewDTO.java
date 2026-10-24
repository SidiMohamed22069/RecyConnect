package com.project.RecyConnect.DTO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
    @Min(value = 1, message = "note entre 1 et 5") @Max(value = 5, message = "note entre 1 et 5")
    private Integer rating;
    @Size(max = 1000, message = "1000 caractères maximum")
    private String comment;

    /** Le lot concerne, pour situer l'avis. */
    private String productTitle;
}
