package com.project.RecyConnect.DTO;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;

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
    
    // Nested info for responses
    private String senderUsername;
    private String receiverUsername;
    private String productTitle;
    private List<String> productImageUrls;
    private String productUnit;
}
