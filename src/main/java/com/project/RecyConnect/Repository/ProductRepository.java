package com.project.RecyConnect.Repository;

import com.project.RecyConnect.Model.Moughataa;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.ProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByUserId(Long userId);
    List<Product> findByCategoryId(Long categoryId);

    /** Nombre d'annonces rattachees a une categorie, avant de la supprimer. */
    long countByCategoryId(Long categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    /**
     * Les annonces disponibles dont le point tombe dans un rectangle.
     *
     * <p>Une requete, et non un filtre en memoire comme le reste du service :
     * une carte redemande ses annonces a chaque deplacement, et relire le
     * catalogue entier a chaque fois ne tient pas — ni pour la base, ni pour le
     * forfait de l'utilisateur. Les colonnes {@code latitude} et
     * {@code longitude} se laissent indexer, ce qu'une distance calculee sur
     * chaque ligne empeche.
     */
    @Query("SELECT p FROM Product p "
            + "WHERE p.status = :status "
            + "AND p.latitude IS NOT NULL AND p.longitude IS NOT NULL "
            + "AND p.latitude BETWEEN :minLat AND :maxLat "
            + "AND p.longitude BETWEEN :minLng AND :maxLng")
    List<Product> findInBounds(@Param("status") ProductStatus status,
                               @Param("minLat") double minLat,
                               @Param("maxLat") double maxLat,
                               @Param("minLng") double minLng,
                               @Param("maxLng") double maxLng);

    /**
     * Les annonces disponibles <b>sans point</b>, rattachees a l'une des
     * moughataas donnees.
     *
     * <p>Sans elles, la carte serait vide : aucune annonce publiee avant son
     * arrivee ne porte de coordonnees. Elles se dessinent au centre de leur
     * quartier, annoncees comme approximatives.
     */
    @Query("SELECT p FROM Product p "
            + "WHERE p.status = :status "
            + "AND (p.latitude IS NULL OR p.longitude IS NULL) "
            + "AND p.location IN :zones")
    List<Product> findWithoutPointInZones(@Param("status") ProductStatus status,
                                          @Param("zones") Collection<Moughataa> zones);
}
