package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.UserDTO;
import com.project.RecyConnect.DTO.UserDeviceDTO;
import com.project.RecyConnect.DTO.UserStatsDTO;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Security.JwtUtil;
import com.project.RecyConnect.Service.UserService;
import com.project.RecyConnect.Service.UserDeviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;
    private final JwtUtil jwtUtil;
    private final UserDeviceService userDeviceService;
    
    public UserController(UserService service, JwtUtil jwtUtil, UserDeviceService userDeviceService) {
        this.service = service;
        this.jwtUtil = jwtUtil;
        this.userDeviceService = userDeviceService;
    }

    @GetMapping
    public List<UserDTO> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public UserDTO create(@RequestBody UserDTO dto) { return service.save(dto); }

    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> update(@PathVariable Long id, @RequestBody UserDTO dto) {
        try {
            return ResponseEntity.ok(service.update(id, dto));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> patch(@PathVariable Long id, @RequestBody UserDTO dto) {
        try {
            User updatedUser = service.patchAndGetUser(id, dto);
            
            // Générer un nouveau token avec les nouvelles données
            String newToken = jwtUtil.generateToken(updatedUser);
            
            // Construire le DTO pour la réponse
            UserDTO userDTO = new UserDTO();
            userDTO.setId(updatedUser.getId());
            userDTO.setUsername(updatedUser.getUsername());
            userDTO.setPhone(updatedUser.getPhone());
            userDTO.setImageData(updatedUser.getImageData());
            userDTO.setRole(updatedUser.getRole());
            
            return ResponseEntity.ok(Map.of(
                "user", userDTO,
                "token", newToken
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/by-phone/{phone}")
    public ResponseEntity<UserDTO> getByPhone(@PathVariable Long phone) {
        return service.findByPhone(phone)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<UserStatsDTO> getUserStats(@PathVariable Long id) {
        return service.getUserStats(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/{id}/fcm-token")
    public ResponseEntity<Void> updateFcmToken(@PathVariable Long id, @RequestBody FcmTokenDTO dto) {
        service.updateFcmToken(id, dto.getToken());
        return ResponseEntity.ok().build();
    }

    /**
     * Enregistrer un nouvel appareil pour un utilisateur
     */
    @PostMapping("/{id}/devices")
    public ResponseEntity<UserDeviceDTO> registerDevice(@PathVariable Long id, @RequestBody DeviceRegistrationDTO dto) {
        UserDeviceDTO device = userDeviceService.registerDevice(
            id, 
            dto.getFcmToken(), 
            dto.getDeviceName(), 
            dto.getDeviceType()
        );
        return ResponseEntity.ok(device);
    }

    /**
     * Récupérer tous les appareils d'un utilisateur
     */
    @GetMapping("/{id}/devices")
    public ResponseEntity<List<UserDeviceDTO>> getUserDevices(@PathVariable Long id) {
        List<UserDeviceDTO> devices = userDeviceService.getUserDevices(id);
        return ResponseEntity.ok(devices);
    }

    /**
     * Récupérer le dernier appareil connecté d'un utilisateur
     */
    @GetMapping("/{id}/devices/last")
    public ResponseEntity<UserDeviceDTO> getLastConnectedDevice(@PathVariable Long id) {
        return userDeviceService.getLastConnectedDevice(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Supprimer un appareil par son token FCM
     */
    @DeleteMapping("/{id}/devices")
    public ResponseEntity<Void> removeDevice(@PathVariable Long id, @RequestBody FcmTokenDTO dto) {
        userDeviceService.removeDevice(dto.getToken());
        return ResponseEntity.ok().build();
    }

    /**
     * Supprimer tous les appareils d'un utilisateur (déconnexion de tous les appareils)
     */
    @DeleteMapping("/{id}/devices/all")
    public ResponseEntity<Void> removeAllDevices(@PathVariable Long id) {
        userDeviceService.removeAllUserDevices(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Changer le rôle d'un utilisateur (réservé aux admins)
     */
    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id, @RequestBody RoleUpdateDTO dto) {
        try {
            Role newRole = Role.valueOf(dto.getRole().toUpperCase());
            UserDTO updatedUser = service.updateRole(id, newRole);
            return ResponseEntity.ok(Map.of(
                "message", "Role updated successfully",
                "userId", updatedUser.getId(),
                "username", updatedUser.getUsername(),
                "role", newRole.name()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid role. Use USER or ADMIN"));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @lombok.Data
    public static class FcmTokenDTO {
        private String token;
    }

    @lombok.Data
    public static class RoleUpdateDTO {
        private String role;
    }
    
    @lombok.Data
    public static class DeviceRegistrationDTO {
        private String fcmToken;
        private String deviceName;
        private String deviceType;  // "ANDROID", "IOS", "WEB"
    }
}
