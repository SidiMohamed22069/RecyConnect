package com.project.RecyConnect.Repository;


import com.project.RecyConnect.Model.Negotiation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NegotiationRepository extends JpaRepository<Negotiation, Long> {
    List<Negotiation> findBySenderId(Long senderId);
    List<Negotiation> findByReceiverId(Long receiverId);
    List<Negotiation> findByProductId(Long productId);
    List<Negotiation> findByProductIdAndStatusIn(Long productId, List<String> statuses);

    @Query("SELECT COALESCE(SUM(n.price * n.quantity), 0) FROM Negotiation n " +
           "WHERE n.product.user.id = :sellerId AND LOWER(n.status) = 'accepted'")
    Double sumAcceptedAmountBySellerId(@Param("sellerId") Long sellerId);

    @Query("SELECT COUNT(n) FROM Negotiation n " +
           "WHERE n.product.user.id = :sellerId AND LOWER(n.status) = 'accepted'")
    Long countAcceptedBySellerId(@Param("sellerId") Long sellerId);
}
