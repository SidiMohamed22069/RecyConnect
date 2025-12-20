package com.project.RecyConnect.DTO;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class CategoryDTO {
    private Long id;
    private OffsetDateTime createdAt;
    private String name;
    private String description;
}
