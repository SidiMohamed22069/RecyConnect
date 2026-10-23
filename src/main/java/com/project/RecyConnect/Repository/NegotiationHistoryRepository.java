package com.project.RecyConnect.Repository;

import java.util.Collection;
import com.project.RecyConnect.Model.NegotiationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NegotiationHistoryRepository extends JpaRepository<NegotiationHistory, Long> {

    List<NegotiationHistory> findByNegotiationIdOrderByCreatedAtAsc(Long negotiationId);

    /** Suppression d'offres : leur fil. */
    void deleteByNegotiationIdIn(Collection<Long> negotiationIds);
    /** Suppression de compte : les entrees qu'il a ecrites. */
    void deleteByAuthorId(Long authorId);
}
