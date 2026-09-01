package com.project.RecyConnect.Repository;

import com.project.RecyConnect.Model.NegotiationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NegotiationHistoryRepository extends JpaRepository<NegotiationHistory, Long> {

    List<NegotiationHistory> findByNegotiationIdOrderByCreatedAtAsc(Long negotiationId);
}
