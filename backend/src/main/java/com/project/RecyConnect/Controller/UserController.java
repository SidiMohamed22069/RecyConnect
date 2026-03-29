package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.UserDTO;
import com.project.RecyConnect.DTO.UserStatsDTO;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Service.UserService;
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
    public UserController(UserService service) { this.service = service; }

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
    public ResponseEntity<UserDTO> patch(@PathVariable Long id, @RequestBody UserDTO dto) {
        try {
            return ResponseEntity.ok(service.patch(id, dto));
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
}
