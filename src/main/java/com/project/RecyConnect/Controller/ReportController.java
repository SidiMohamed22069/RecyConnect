package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.ReportDTO;
import com.project.RecyConnect.Model.ReportStatus;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Service.ModerationService;
import com.project.RecyConnect.Service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Les signalements : depot par les utilisateurs, traitement par la moderation.
 *
 * <p>Le depot est ouvert a tout compte authentifie — c'est le dispositif que
 * Google Play exige d'une application qui publie du contenu d'utilisateurs. La
 * file, elle, n'est visible que des administrateurs.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ModerationService moderationService;
    private final UserService userService;

    public ReportController(ModerationService moderationService, UserService userService) {
        this.moderationService = moderationService;
        this.userService = userService;
    }

    /**
     * Signaler une annonce ou un compte.
     *
     * <p>L'auteur du signalement vient du jeton : un corps de requete ne peut
     * pas designer quelqu'un d'autre.
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody ReportDTO request) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            ReportDTO created = moderationService.report(currentUser, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** La file de moderation. Sans {@code status}, tout l'historique. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> list(@RequestParam(required = false) String status) {
        ReportStatus filter;
        try {
            filter = status == null || status.isBlank()
                    ? null
                    : ReportStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Unknown status. Use PENDING, REVIEWING, ACTIONED or REJECTED"));
        }
        List<ReportDTO> reports = moderationService.listReports(filter);
        return ResponseEntity.ok(reports);
    }

    /** Ce qui attend d'etre examine : la pastille de l'interface d'administration. */
    @GetMapping("/pending/count")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Long> pendingCount() {
        return Map.of("count", moderationService.countPending());
    }

    /**
     * Consigner le traitement d'un signalement.
     *
     * <p>Le moderateur et la date sont releves par le serveur : c'est la
     * tracabilite qu'attend le reglement, elle ne doit pas dependre de ce que
     * le client veut bien envoyer.
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> handle(@PathVariable Long id, @RequestBody HandleRequest request) {
        User moderator = userService.getCurrentUser();
        if (moderator == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ReportStatus status;
        try {
            status = ReportStatus.valueOf(String.valueOf(request.getStatus()).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Unknown status. Use PENDING, REVIEWING, ACTIONED or REJECTED"));
        }
        try {
            return ResponseEntity.ok(
                    moderationService.handle(id, status, request.getResolution(), moderator));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @lombok.Data
    public static class HandleRequest {
        private String status;
        private String resolution;
    }
}
