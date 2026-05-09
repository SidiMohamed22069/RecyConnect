package com.project.RecyConnect.Service;

import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Model.UserSession;
import com.project.RecyConnect.Repository.UserRepo;
import com.project.RecyConnect.Repository.UserSessionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserSessionService {

    private final UserSessionRepository userSessionRepository;
    private final UserRepo userRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public UserSessionService(UserSessionRepository userSessionRepository, UserRepo userRepo) {
        this.userSessionRepository = userSessionRepository;
        this.userRepo = userRepo;
    }

    @Transactional
    public SessionReplacementResult replaceSession(Long userId, String deviceId, String deviceName, String fcmToken) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserSession current = entityManager.find(UserSession.class, userId);

        Long nextVersion = current == null ? 1L : current.getSessionVersion() + 1L;
        String previousFcmToken = null;

        if (current != null && current.getDeviceId() != null && !current.getDeviceId().equals(deviceId)) {
            previousFcmToken = current.getFcmToken();
        }

        if (current != null) {
            current.setUser(user);
            current.setDeviceId(deviceId);
            current.setDeviceName(deviceName);
            current.setFcmToken(fcmToken);
            current.setSessionVersion(nextVersion);
            return new SessionReplacementResult(current, previousFcmToken);
        }

        UserSession next = UserSession.builder()
                .userId(userId)
                .user(user)
                .deviceId(deviceId)
                .deviceName(deviceName)
                .fcmToken(fcmToken)
                .sessionVersion(nextVersion)
                .build();

        entityManager.persist(next);
        return new SessionReplacementResult(next, previousFcmToken);
    }

    public Optional<UserSession> findByUserId(Long userId) {
        return userSessionRepository.findById(userId);
    }

    @Transactional
    public void revokeIfCurrent(Long userId, Long sessionVersion, String deviceId) {
        Optional<UserSession> current = userSessionRepository.findById(userId);
        if (current.isEmpty()) {
            return;
        }

        UserSession session = current.get();
        if (session.getSessionVersion().equals(sessionVersion) && session.getDeviceId().equals(deviceId)) {
            userSessionRepository.deleteById(userId);
        }
    }

    public record SessionReplacementResult(UserSession session, String previousFcmToken) {
    }
}
