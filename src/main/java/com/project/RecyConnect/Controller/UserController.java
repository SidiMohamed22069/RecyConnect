package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.UserDTO;
import com.project.RecyConnect.DTO.UserStatsDTO;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Model.UserSession;
import com.project.RecyConnect.DTO.BlockedUserDTO;
import com.project.RecyConnect.Security.JwtUtil;
import com.project.RecyConnect.Service.AccountDeletionService;
import com.project.RecyConnect.Service.ModerationService;
import com.project.RecyConnect.Service.PhoneVerificationService;
import com.project.RecyConnect.Service.UserService;
import com.project.RecyConnect.Service.UserSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final AccountDeletionService accountDeletionService;
    private final ModerationService moderationService;
    private final PasswordEncoder passwordEncoder;

    /** Longueur minimale d'un mot de passe, alignee sur l'inscription mobile. */
    private static final int MIN_PASSWORD_LENGTH = 6;

    public UserController(UserService service,
                          JwtUtil jwtUtil,
                          UserSessionService userSessionService,
                          AccountDeletionService accountDeletionService,
                          ModerationService moderationService,
                          PasswordEncoder passwordEncoder) {
        this.service = service;
        this.jwtUtil = jwtUtil;
        this.userSessionService = userSessionService;
        this.accountDeletionService = accountDeletionService;
        this.moderationService = moderationService;
        this.passwordEncoder = passwordEncoder;
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

    /**
     * Creation d'un compte par un administrateur.
     *
     * <p>L'inscription mobile ({@code /api/auth/register}) ne convient pas ici:
     * elle envoie un code par SMS au futur utilisateur, que l'administrateur
     * n'a pas sous la main. Le mot de passe est donc choisi par l'appelant et
     * le compte est utilisable immediatement.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> create(@RequestBody UserDTO dto) {
        if (dto.getUsername() == null || dto.getUsername().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username is required"));
        }
        if (dto.getPassword() == null || dto.getPassword().length() < MIN_PASSWORD_LENGTH) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Password must be at least " + MIN_PASSWORD_LENGTH + " characters"));
        }

        Long phone = normalizePhone(dto.getPhone());
        if (phone == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Numero de telephone invalide"));
        }

        String username = dto.getUsername().trim();
        if (service.phoneExists(phone)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Phone number already exists"));
        }
        if (service.usernameExists(username)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Username already exists"));
        }

        UserDTO created = service.createAccount(
                username, phone, passwordEncoder.encode(dto.getPassword()), dto.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Reinitialisation du mot de passe d'un compte par un administrateur.
     *
     * <p>Le chemin normal passe par un code SMS ({@code /api/auth/reset-password}).
     * Celui-ci existe pour les comptes qui ne recoivent plus leurs SMS. Les
     * sessions ouvertes sont revoquees: un mot de passe remplace doit
     * deconnecter les appareils qui se servaient de l'ancien.
     */
    @PatchMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> resetPassword(@PathVariable Long id,
                                           @RequestBody(required = false) PasswordResetRequest request) {
        String password = request == null ? null : request.getPassword();
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Password must be at least " + MIN_PASSWORD_LENGTH + " characters"));
        }

        try {
            service.setPassword(id, passwordEncoder.encode(password));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }

        userSessionService.revokeAllSessionsForUser(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Numero tel qu'il est stocke: huit chiffres, sans l'indicatif 222.
     *
     * <p>Meme normalisation que l'inscription mobile, pour qu'un compte cree
     * ici puisse se connecter avec le numero que son proprietaire compose.
     */
    private Long normalizePhone(Long phone) {
        if (phone == null) {
            return null;
        }
        String digits = PhoneVerificationService.toLocalPhone(String.valueOf(phone));
        return digits != null && digits.matches("[0-9]{8}")
                ? Long.parseLong(digits)
                : null;
    }

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
     * Suppression definitive d'un compte.
     *
     * <p>Exigee par le reglement "Donnees utilisateur" de Google Play, qui
     * impose a toute application creant des comptes un chemin de suppression
     * depuis l'application. Ce que la suppression emporte reellement — annonces,
     * photos sur le disque, offres, notifications, session, blocages — est
     * detaille dans {@link AccountDeletionService} ; l'appel precedent se
     * contentait d'un {@code deleteById} qui echouait des que le compte avait
     * servi.
     *
     * <p>Supprimer <em>son propre</em> compte demande le mot de passe : un
     * telephone laisse deverrouille ne doit pas suffire a effacer un compte.
     * L'echec est rendu en 403 — l'application y lit "mot de passe refuse" et
     * non "session expiree", et ne deconnecte donc pas l'utilisateur.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id,
                                    @RequestBody(required = false) DeleteAccountRequest request) {
        User currentUser = service.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isSelf = currentUser.getId().equals(id);
        if (!isAdmin && !isSelf) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You can only modify your own account"));
        }

        if (isSelf) {
            String password = request == null ? null : request.getPassword();
            if (password == null || password.isBlank()
                    || !passwordEncoder.matches(password, currentUser.getPassword())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Invalid password"));
            }
        }

        try {
            accountDeletionService.deleteAccount(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Blocages entre utilisateurs
    //
    // Le reglement "Contenu genere par les utilisateurs" exige qu'un
    // utilisateur puisse en bloquer un autre et cesser de voir ce qu'il
    // publie. L'application tient une liste locale pour que le blocage soit
    // immediat ; ces trois routes sont ce qui lui permet de survivre a un
    // changement d'appareil, et au serveur de filtrer a la source.
    // ------------------------------------------------------------------

    @PostMapping("/{id}/block")
    public ResponseEntity<?> block(@PathVariable Long id) {
        User currentUser = service.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            moderationService.block(currentUser, id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/block")
    public ResponseEntity<?> unblock(@PathVariable Long id) {
        User currentUser = service.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        moderationService.unblock(currentUser, id);
        return ResponseEntity.noContent().build();
    }

    /** Les comptes que l'appelant a bloques. */
    @GetMapping("/me/blocks")
    public ResponseEntity<List<BlockedUserDTO>> myBlocks() {
        User currentUser = service.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(moderationService.listBlocked(currentUser.getId()));
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
    @RequestMapping(value = "/{id}/role", method = { RequestMethod.PUT, RequestMethod.PATCH })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id, @RequestBody RoleUpdateDTO dto) {
        try {
            Role newRole = Role.valueOf(dto.getRole().toUpperCase());

            // Un administrateur ne peut pas se retirer ses propres droits: le
            // panneau l'interdit deja dans l'interface, mais la garantie doit
            // etre ici, sous peine de se retrouver sans aucun administrateur.
            User currentUser = service.getCurrentUser();
            if (newRole != Role.ADMIN && currentUser != null && currentUser.getId().equals(id)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "An administrator cannot revoke their own privileges"));
            }

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

    /** Le mot de passe qu'un administrateur impose a un compte. */
    @lombok.Data
    public static class PasswordResetRequest {
        private String password;
    }

    /** Le mot de passe qui accompagne une suppression de compte. */
    @lombok.Data
    public static class DeleteAccountRequest {
        private String password;
    }
}
