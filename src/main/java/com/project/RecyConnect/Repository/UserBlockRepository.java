package com.project.RecyConnect.Repository;

import com.project.RecyConnect.Model.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    Optional<UserBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    List<UserBlock> findByBlockerIdOrderByCreatedAtDesc(Long blockerId);

    /**
     * Les comptes qu'un utilisateur ne doit plus voir : ceux qu'il a bloques,
     * et ceux qui l'ont bloque.
     *
     * <p>Le blocage est declare dans un sens et s'applique dans les deux : un
     * utilisateur bloque ne doit pas pouvoir continuer a atteindre celui qui
     * l'a bloque en passant par la recherche.
     */
    @Query("SELECT CASE WHEN b.blocker.id = :userId THEN b.blocked.id ELSE b.blocker.id END "
            + "FROM UserBlock b WHERE b.blocker.id = :userId OR b.blocked.id = :userId")
    List<Long> findHiddenUserIds(@Param("userId") Long userId);

    /** Efface les blocages d'un compte supprime, dans les deux sens. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserBlock b WHERE b.blocker.id = :userId OR b.blocked.id = :userId")
    void deleteAllInvolving(@Param("userId") Long userId);
}
