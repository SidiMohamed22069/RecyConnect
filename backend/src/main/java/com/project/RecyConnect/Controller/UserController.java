package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.UserDTO;
import com.project.RecyConnect.DTO.UserStatsDTO;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Model.UserSession;
import com.project.RecyConnect.Security.JwtUtil;
import com.project.RecyConnect.Service.UserService;
import com.project.RecyConnect.Service.UserSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;
    private final JwtUtil jwtUtil;
    private final UserSessionService userSessionService;
    
    public UserController(UserService service, JwtUtil jwtUtil, UserSessionService userSessionService) {
        this.service = service;
        this.jwtUtil = jwtUtil;
        this.userSessionService = userSessionService;
    }

    /** Lister tous les comptes est une operation d'administration. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDTO> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public UserDTO create(@RequestBody UserDTO dto) { return service.save(dto); }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody UserDTO dto) {
        ResponseEntity<?> denied = denyIfNotSelfOrAdmin(id);
        if (denied != null) {
            return denied;
        }
        try {
            return ResponseEntity.ok(service.update(id, dto));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> patch(@PathVariable Long id, @RequestBody UserDTO dto) {
        ResponseEntity<?> denied = denyIfNotSelfOrAdmin(id);
        if (denied != null) {
            return denied;
        }
        try {
            User updatedUser = service.patchAndGetUser(id, dto);
            
                // Générer un token cohérent avec la session active (si présente)
                String newToken;
                Optional<UserSession> activeSession = userSessionService.findByUserId(updatedUser.getId());
                if (activeSession.isPresent()) {
                    UserSession session = activeSession.get();
                    newToken = jwtUtil.generateToken(updatedUser, session.getSessionVersion(), session.getDeviceId());
                } else {
                    newToken = jwtUtil.generateToken(updatedUser);
                }
            
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

    /**
     * Suppression d'un compte.
     * ATTENTION: l'entite User cascade sur products / negotiationsSent /
     * negotiationsReceived, la suppression est donc destructrice au-dela du compte.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        ResponseEntity<?> denied = denyIfNotSelfOrAdmin(id);
        if (denied != null) {
            return denied;
        }
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Autorise l'operation uniquement si l'appelant agit sur son propre compte,
     * ou s'il est administrateur.
     *
     * @return null si l'operation est autorisee, sinon la reponse d'erreur a renvoyer.
     */
    private ResponseEntity<?> denyIfNotSelfOrAdmin(Long targetUserId) {
        User currentUser = service.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isSelf = currentUser.getId().equals(targetUserId);
        if (!isAdmin && !isSelf) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You can only modify your own account"));
        }
        return null;
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
    public static class RoleUpdateDTO {
        private String role;
    }
}
