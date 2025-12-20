package com.project.RecyConnect.DTO;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class NegotiationDTO {
    private Long id;
    private OffsetDateTime createdAt;
    private Long senderId;
    private Long receiverId;
    private Long productId;
    private String status;
    private Double price;
    private Integer quantity;
}
