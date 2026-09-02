package com.project.RecyConnect.DTO;

import com.project.RecyConnect.Model.GeoPrecision;
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

    /**
     * Le point du lot, <b>tel qu'il doit etre montre a l'appelant</b>.
     *
     * <p>Ce n'est pas toujours le point stocke: pour une annonce en precision
     * approximative, le serveur n'envoie a un tiers que le centre d'une case de
     * 300 m. Le point exact ne sort que pour le vendeur lui-meme et pour
     * l'acheteur dont l'offre a ete acceptee.
     *
     * <p>Le filtrage a lieu ici, a l'ecriture de la reponse, et non dans
     * l'application: une protection appliquee par le client n'en est pas une,
     * la valeur precise ayant deja quitte le serveur.
     */
    private Double latitude;
    private Double longitude;

    /** Ce que le vendeur a accepte de montrer, ou {@code null} sans point. */
    private GeoPrecision geoPrecision;

    /**
     * Distance depuis le point demande, en kilometres.
     *
     * <p>Renseignee par les seules lectures qui ont un centre — {@code /nearby}
     * et une recherche par rayon. Ailleurs, c'est au client de la calculer s'il
     * connait sa position.
     */
    private Double distanceKm;

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
