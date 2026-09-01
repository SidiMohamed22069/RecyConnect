package com.project.RecyConnect.DTO;

import com.project.RecyConnect.Model.Moughataa;
import com.project.RecyConnect.Model.ProductStatus;
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
    private ProductStatus status;

    /** Moughataa declaree a la publication, ou {@code null}. */
    private Moughataa location;

    private List<String> imageUrls;
    private Long categoryId;
    private Long userId;
    
    // For nested responses
    private String categoryName;
    private String userName;
    private Long userPhone;

    /**
     * Nombre d'offres en attente sur l'annonce.
     *
     * <p>Sert la preuve sociale de la fiche produit ("3 offres en cours") et
     * evite a l'application une seconde requete — d'autant que la file des
     * offres, elle, n'est lisible que par le vendeur.
     *
     * <p>Renseigne uniquement sur la fiche d'une annonce ({@code GET
     * /api/products/{id}}): le calculer pour chaque ligne d'une liste de
     * recherche couterait une requete par annonce.
     */
    private Long pendingOffersCount;

    /** Vrai si l'appelant a enregistre cette annonce dans ses favoris. */
    private Boolean favorite;
}
