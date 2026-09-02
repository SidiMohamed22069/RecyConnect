package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.ReviewDTO;
import com.project.RecyConnect.DTO.ReviewSummaryDTO;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Service.ReviewService;
import com.project.RecyConnect.Service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Les avis laisses sur les vendeurs.
 *
 * <p>La lecture est publique — une note qui ne se verrait qu'une fois connecte
 * ne rassurerait personne au moment ou l'on hesite. L'ecriture, elle, exige
 * d'etre l'acheteur d'une offre acceptee.
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService service;
    private final UserService userService;

    public ReviewController(ReviewService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    /** Les avis recus par un vendeur. */
    @GetMapping("/user/{userId}")
    public List<ReviewDTO> forUser(@PathVariable Long userId) {
        return service.findForUser(userId);
    }

    /** La moyenne d'un vendeur et le nombre d'avis. */
    @GetMapping("/user/{userId}/summary")
    public ReviewSummaryDTO summary(@PathVariable Long userId) {
        return service.summaryFor(userId);
    }

    /**
     * Les transactions que l'appelant peut encore noter.
     *
     * <p>Rend des identifiants d'offres, pas des fiches: l'application les a
     * deja dans sa liste "mes offres envoyees" et n'a besoin que de savoir
     * lesquelles attendent une note.
     */
    @GetMapping("/pending/me")
    public ResponseEntity<List<Long>> pending() {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(service.pendingFor(currentUser.getId()));
    }

    /**
     * Depose un avis.
     *
     * <p>Les refus sont distingues par leur code: 403 si l'appelant n'est pas
     * l'acheteur, 409 si l'offre n'est pas acceptee ou deja notee, 400 si la
     * note est hors echelle. Un client doit pouvoir dire pourquoi sans lire un
     * message — et un 403 sur une offre en attente enverrait l'application
     * mobile sur son ecran de connexion.
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody ReviewDTO dto) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ReviewService.ReviewResult result = service.create(
                dto.getNegotiationId(), currentUser.getId(), dto.getRating(), dto.getComment());

        return switch (result.outcome()) {
            case OK -> ResponseEntity.status(HttpStatus.CREATED).body(result.review());
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case NOT_THE_BUYER -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Only the buyer of an accepted offer can leave a review"));
            case NOT_ACCEPTED -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "A review is available once the offer is accepted"));
            case ALREADY_REVIEWED -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "This transaction has already been reviewed"));
            case INVALID_RATING -> ResponseEntity.badRequest()
                    .body(Map.of("message", "Rating must be between 1 and 5"));
        };
    }
}
