package com.project.RecyConnect.DTO;

import lombok.Data;
import java.time.OffsetDateTime;

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
    private String imageUrl;
    private Long categoryId;
    private Long userId;
}
