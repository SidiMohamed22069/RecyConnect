package com.project.RecyConnect.DTO;

import com.project.RecyConnect.Model.Moughataa;
import lombok.Data;

import java.time.OffsetDateTime;

/** Une veille: "prevenez-moi quand du cuivre passe sous 300 MRU/kg". */
@Data
public class SearchAlertDTO {
    private Long id;
    private OffsetDateTime createdAt;
    private Long userId;
    private String keyword;
    private Long categoryId;
    private String categoryName;
    private Double maxPrice;
    private Long minQuantity;
    private Moughataa location;
    private Boolean active;
}
