package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.NotificationDTO;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Service.NotificationService;
import com.project.RecyConnect.Service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Boite de reception.
 *
 * <p>Chaque lecture ou modification est rapportee au destinataire : avant,
 * n'importe quel compte lisait toutes les boites, supprimait les notifications
 * d'autrui et pouvait meme en creer — donc declencher un push — vers
 * n'importe qui. La creation libre est reservee a l'administration
 * ({@code /api/admin/notifications/broadcast} existe pour les annonces
 * generales).
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;
    private final UserService userService;

    public NotificationController(NotificationService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<NotificationDTO> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return withOwned(id, ResponseEntity::ok);
    }

    @GetMapping("/receiver/{receiverId}")
    public ResponseEntity<?> getByReceiver(@PathVariable Long receiverId) {
        ResponseEntity<?> denied = denyIfNotSelfOrAdmin(receiverId);
        return denied != null ? denied : ResponseEntity.ok(service.findByReceiverId(receiverId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public NotificationDTO create(@RequestBody NotificationDTO dto) { return service.save(dto); }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody NotificationDTO dto) {
        return withOwned(id, existing -> ResponseEntity.ok(service.update(id, sanitized(dto))));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> patch(@PathVariable Long id, @RequestBody NotificationDTO dto) {
        return withOwned(id, existing -> ResponseEntity.ok(service.patch(id, sanitized(dto))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return withOwned(id, existing -> {
            service.delete(id);
            return ResponseEntity.noContent().build();
        });
    }

    @GetMapping("/receiver/{receiverId}/unread")
    public ResponseEntity<?> getUnreadByReceiver(@PathVariable Long receiverId) {
        ResponseEntity<?> denied = denyIfNotSelfOrAdmin(receiverId);
        return denied != null ? denied : ResponseEntity.ok(service.getUnreadNotifications(receiverId));
    }

    @GetMapping("/receiver/{receiverId}/unread/count")
    public ResponseEntity<?> getUnreadCountByReceiver(@PathVariable Long receiverId) {
        ResponseEntity<?> denied = denyIfNotSelfOrAdmin(receiverId);
        return denied != null ? denied : ResponseEntity.ok(service.countUnreadNotifications(receiverId));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id) {
        return withOwned(id, existing -> {
            service.markAsRead(id);
            return ResponseEntity.ok().build();
        });
    }

    // ------------------------------------------------------------------

    /** Un destinataire ne change jamais de mains par une retouche. */
    private NotificationDTO sanitized(NotificationDTO dto) {
        dto.setSenderId(null);
        dto.setReceiverId(null);
        return dto;
    }

    private ResponseEntity<?> denyIfNotSelfOrAdmin(Long userId) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (currentUser.getRole() != Role.ADMIN && !currentUser.getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return null;
    }

    /** Execute {@code action} si la notification existe et appartient a l'appelant. */
    private ResponseEntity<?> withOwned(Long id,
                                        java.util.function.Function<NotificationDTO, ResponseEntity<?>> action) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        NotificationDTO existing = service.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        if (!isAdmin && !currentUser.getId().equals(existing.getReceiverId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            return action.apply(existing);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
