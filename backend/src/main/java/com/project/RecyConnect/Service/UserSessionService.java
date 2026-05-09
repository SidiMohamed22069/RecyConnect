package com.project.RecyConnect.Service;

import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Model.UserSession;
import com.project.RecyConnect.Repository.UserRepo;
import com.project.RecyConnect.Repository.UserSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserSessionService {

    private final UserSessionRepository userSessionRepository;
    private final UserRepo userRepo;

    public UserSessionService(UserSessionRepository userSessionRepository, UserRepo userRepo) {
        this.userSessionRepository = userSessionRepository;
        this.userRepo = userRepo;
    }

    @Transactional
    public SessionReplacementResult replaceSession(Long userId, String deviceId, String deviceName, String fcmToken) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserSession current = userSessionRepository.findById(userId).orElse(null);

        Long nextVersion = current == null ? 1L : current.getSessionVersion() + 1L;
        String previousFcmToken = null;

        if (current != null && current.getDeviceId() != null && !current.getDeviceId().equals(deviceId)) {
            previousFcmToken = current.getFcmToken();
        }

        UserSession next = UserSession.builder()
                .userId(userId)
                .user(user)
                .deviceId(deviceId)
                .deviceName(deviceName)
                .fcmToken(fcmToken)
                .sessionVersion(nextVersion)
                .createdAt(current == null ? null : current.getCreatedAt())
                .build();

        UserSession saved = userSessionRepository.save(next);
        return new SessionReplacementResult(saved, previousFcmToken);
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
