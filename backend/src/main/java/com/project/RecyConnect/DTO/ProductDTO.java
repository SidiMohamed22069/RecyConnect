package com.project.RecyConnect.DTO;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;

@Data
public class ProductDTO {
    private Long id;
    private OffsetDateTime createdAt;
    private String title;
    private String desc;
    private Double price;
    private String unit;
    private Long quantityTotal;
    private Long quantityAvailable;
    private String status;
    private List<String> imageUrls;
    private Long categoryId;
    private Long userId;
    
    // For nested responses
    private String categoryName;
    private String userName;
    private String userImage;
}
