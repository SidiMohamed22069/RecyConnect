package com.project.RecyConnect.DTO;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class UserStatsDTO {
    private Long id;
    private OffsetDateTime createdAt;
    private Long userId;
    private Integer totalProducts;
    private Integer recycledCount;
    private Integer availableCount;
    private String recyclingRate;
}
