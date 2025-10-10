package com.project.RecyConnect.Repository;


import com.project.RecyConnect.Model.Negotiation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NegotiationRepository extends JpaRepository<Negotiation, Long> {
    List<Negotiation> findBySenderId(Long senderId);
    List<Negotiation> findByReceiverId(Long receiverId);
    List<Negotiation> findByProductId(Long productId);
}
