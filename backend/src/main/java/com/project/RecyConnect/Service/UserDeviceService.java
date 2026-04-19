package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.UserDeviceDTO;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Model.UserDevice;
import com.project.RecyConnect.Repository.UserDeviceRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserDeviceService {

    private final UserDeviceRepository deviceRepository;
    private final UserRepo userRepo;

    public UserDeviceService(UserDeviceRepository deviceRepository, UserRepo userRepo) {
        this.deviceRepository = deviceRepository;
        this.userRepo = userRepo;
    }

    /**
     * Enregistre ou met à jour un appareil pour un utilisateur
     */
    @Transactional
    public UserDeviceDTO registerDevice(Long userId, String fcmToken, String deviceName, String deviceType) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Vérifier si ce token existe déjà
        Optional<UserDevice> existingDevice = deviceRepository.findByFcmToken(fcmToken);

        UserDevice device;
        if (existingDevice.isPresent()) {
            // Mettre à jour l'appareil existant
            device = existingDevice.get();
            device.setUser(user);  // Au cas où le token a été transféré à un autre utilisateur
            device.setDeviceName(deviceName);
            device.setDeviceType(deviceType);
            device.setLastConnectedAt(OffsetDateTime.now());
        } else {
            // Créer un nouvel appareil
            device = UserDevice.builder()
                    .user(user)
                    .fcmToken(fcmToken)
                    .deviceName(deviceName)
                    .deviceType(deviceType)
                    .lastConnectedAt(OffsetDateTime.now())
                    .createdAt(OffsetDateTime.now())
                    .build();
        }

        UserDevice saved = deviceRepository.save(device);
        return toDTO(saved);
    }

    /**
     * Récupère tous les appareils d'un utilisateur
     */
    public List<UserDeviceDTO> getUserDevices(Long userId) {
        return deviceRepository.findByUserId(userId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Récupère tous les tokens FCM d'un utilisateur
     */
    public List<String> getUserFcmTokens(Long userId) {
        return deviceRepository.findByUserId(userId)
                .stream()
                .map(UserDevice::getFcmToken)
                .collect(Collectors.toList());
    }

    /**
     * Récupère le dernier appareil connecté d'un utilisateur
     */
    public Optional<UserDeviceDTO> getLastConnectedDevice(Long userId) {
        return deviceRepository.findTopByUserIdOrderByLastConnectedAtDesc(userId)
                .map(this::toDTO);
    }

    /**
     * Supprime TOUS les appareils et enregistre le nouveau
     * Un seul appareil connecté par compte à la fois
     */
    @Transactional
    public UserDeviceDTO replaceAllDevicesWithNew(Long userId, String fcmToken, String deviceName, String deviceType) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 1. Supprimer tous les anciens appareils de cet utilisateur
        deviceRepository.deleteByUserId(userId);

        // 2. Créer le nouvel appareil
        UserDevice device = UserDevice.builder()
                .user(user)
                .fcmToken(fcmToken)
                .deviceName(deviceName)
                .deviceType(deviceType)
                .lastConnectedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .build();

        UserDevice saved = deviceRepository.save(device);
        return toDTO(saved);
    }

    /**
     * Supprime un appareil par son token FCM
     */
    @Transactional
    public void removeDevice(String fcmToken) {
        deviceRepository.deleteByFcmToken(fcmToken);
    }

    /**
     * Supprime tous les appareils d'un utilisateur
     */
    @Transactional
    public void removeAllUserDevices(Long userId) {
        deviceRepository.deleteByUserId(userId);
    }

    /**
     * Met à jour la date de dernière connexion d'un appareil
     */
    @Transactional
    public void updateLastConnected(String fcmToken) {
        deviceRepository.findByFcmToken(fcmToken).ifPresent(device -> {
            device.setLastConnectedAt(OffsetDateTime.now());
            deviceRepository.save(device);
        });
    }

    /**
     * Invalide un token (le supprime)
     */
    @Transactional
    public void invalidateToken(String fcmToken) {
        deviceRepository.deleteByFcmToken(fcmToken);
    }

    private UserDeviceDTO toDTO(UserDevice device) {
        return UserDeviceDTO.builder()
                .id(device.getId())
                .userId(device.getUser().getId())
                .fcmToken(device.getFcmToken())
                .deviceName(device.getDeviceName())
                .deviceType(device.getDeviceType())
                .lastConnectedAt(device.getLastConnectedAt())
                .createdAt(device.getCreatedAt())
                .build();
    }
}
