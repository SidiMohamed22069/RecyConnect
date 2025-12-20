package com.project.RecyConnect.Repository;

import com.project.RecyConnect.Model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findBySenderId(Long userId);
    List<Notification> findByReceiverId(Long userId);
}
