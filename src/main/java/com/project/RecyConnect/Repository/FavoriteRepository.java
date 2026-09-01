package com.project.RecyConnect.Repository;

import com.project.RecyConnect.Model.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Favorite> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserIdAndProductId(Long userId, Long productId);

    /** Les annonces enregistrees par l'appelant, parmi celles affichees. */
    List<Favorite> findByUserIdAndProductIdIn(Long userId, List<Long> productIds);

    long countByProductId(Long productId);
}
