package com.project.RecyConnect.DTO;

import lombok.Data;

import java.time.OffsetDateTime;

/** Un tour de negociation, tel qu'affiche dans le fil "25 -> 20 -> 22". */
@Data
public class NegotiationHistoryDTO {
    private Long id;
    private OffsetDateTime createdAt;
    private Long negotiationId;
    private Long authorId;
    private String authorUsername;

    /** {@code OFFER}, {@code COUNTER_OFFER} ou {@code UPDATED}. */
    private String kind;

    private Double price;
    private Integer quantity;
    private Double totalAmount;
}
