package com.project.RecyConnect.DTO;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Une transaction conclue, vue depuis le compte qui la consulte.
 *
 * <p>{@code role} dit de quel cote il se trouvait — {@code SELLER} ou
 * {@code BUYER} —, ce qui evite a l'application de recomparer des
 * identifiants pour savoir si la ligne est une vente ou un achat.
 */
@Data
public class TransactionDTO {
    private Long negotiationId;
    private OffsetDateTime date;
    private String role;
    private Long productId;
    private String productTitle;
    private String productUnit;
    private Long counterpartId;
    private String counterpartUsername;
    private Integer quantity;
    private Double unitPrice;
    private Double totalAmount;
}
