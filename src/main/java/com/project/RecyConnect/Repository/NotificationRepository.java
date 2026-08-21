package com.project.RecyConnect.Repository;

import com.project.RecyConnect.Model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findBySenderId(Long userId);
    List<Notification> findByReceiverId(Long userId);
    
    /**
     * Compte les notifications non lues d'un utilisateur
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.receiver.id = :receiverId AND (n.isRead = false OR n.isRead IS NULL)")
    long countUnreadByReceiverId(@Param("receiverId") Long receiverId);
}
