package com.project.RecyConnect.DTO;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import com.project.RecyConnect.Model.Moughataa;
import lombok.Data;

import java.time.OffsetDateTime;

/** Une veille: "prevenez-moi quand du cuivre passe sous 300 MRU/kg". */
@Data
public class SearchAlertDTO {
    private Long id;
    private OffsetDateTime createdAt;
    private Long userId;
    @Size(max = 255, message = "255 caractères maximum")
    private String keyword;
    private Long categoryId;
    private String categoryName;
    @PositiveOrZero(message = "doit être positif")
    private Double maxPrice;
    @PositiveOrZero(message = "doit être positive")
    private Long minQuantity;
    private Moughataa location;
    private Boolean active;
}
