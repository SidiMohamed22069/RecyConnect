package com.project.RecyConnect.Repository;

import com.project.RecyConnect.Model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByTargetIdOrderByCreatedAtDesc(Long targetId);

    Optional<Review> findByNegotiationId(Long negotiationId);

    boolean existsByNegotiationId(Long negotiationId);

    long countByTargetId(Long targetId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.target.id = :targetId")
    Double averageRatingByTargetId(@Param("targetId") Long targetId);
}
