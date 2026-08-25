package com.project.RecyConnect.Repository;

import com.project.RecyConnect.Model.Report;
import com.project.RecyConnect.Model.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    /** File de moderation : le plus ancien d'abord, c'est celui qui attend. */
    List<Report> findByStatusOrderByCreatedAtAsc(ReportStatus status);

    List<Report> findAllByOrderByCreatedAtDesc();

    long countByStatus(ReportStatus status);

    /** Les signalements deja recus sur un contenu donne. */
    List<Report> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);

    /**
     * Un utilisateur ne signale un meme contenu qu'une fois tant que le
     * signalement n'a pas ete traite.
     */
    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
            Long reporterId, String targetType, Long targetId, ReportStatus status);

    /**
     * Detache les signalements d'un compte supprime sans les effacer.
     *
     * <p>C'est ce que la politique de confidentialite annonce : la trace d'un
     * signalement traite survit au compte, mais elle ne designe plus personne.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Report r SET r.reporter = NULL WHERE r.reporter.id = :userId")
    void detachReporter(@Param("userId") Long userId);

    /** Meme traitement pour le moderateur qui a traite le signalement. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Report r SET r.handledBy = NULL WHERE r.handledBy.id = :userId")
    void detachHandler(@Param("userId") Long userId);
}
