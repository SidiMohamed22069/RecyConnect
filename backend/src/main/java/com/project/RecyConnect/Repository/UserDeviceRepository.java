package com.project.RecyConnect.Repository;

import com.project.RecyConnect.Model.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    
    List<UserDevice> findByUserId(Long userId);
    
    Optional<UserDevice> findByFcmToken(String fcmToken);
    
    Optional<UserDevice> findTopByUserIdOrderByLastConnectedAtDesc(Long userId);
    
    void deleteByFcmToken(String fcmToken);
    
    void deleteByUserId(Long userId);
    
    boolean existsByFcmToken(String fcmToken);
}
