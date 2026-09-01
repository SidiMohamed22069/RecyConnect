package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.ReviewDTO;
import com.project.RecyConnect.DTO.ReviewSummaryDTO;
import com.project.RecyConnect.Model.Negotiation;
import com.project.RecyConnect.Model.NegotiationStatus;
import com.project.RecyConnect.Model.Review;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.NegotiationRepository;
import com.project.RecyConnect.Repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Les avis laisses apres une transaction.
 *
 * <p>Le droit d'ecrire ne s'achete pas: il naît d'une offre acceptee, et
 * seulement de celle-la. C'est ce qui separe une note d'un commentaire
 * anonyme, et ce qui rend la moyenne opposable.
 */
@Service
public class ReviewService {

    private final ReviewRepository repo;
    private final NegotiationRepository negotiationRepo;

    public ReviewService(ReviewRepository repo, NegotiationRepository negotiationRepo) {
        this.repo = repo;
        this.negotiationRepo = negotiationRepo;
    }

    /** Le refus oppose a une tentative d'avis, ou {@link #OK}. */
    public enum Outcome {
        OK,
        /** L'offre designee n'existe pas. */
        NOT_FOUND,
        /** L'appelant n'est pas l'acheteur de cette offre. */
        NOT_THE_BUYER,
        /** L'offre n'a pas ete acceptee: rien ne s'est encore passe a noter. */
        NOT_ACCEPTED,
        /** Un avis a deja ete laisse sur cette transaction. */
        ALREADY_REVIEWED,
        /** Note absente ou hors de l'echelle de 1 a 5. */
        INVALID_RATING
    }

    /** Le verdict, et l'avis enregistre s'il est favorable. */
    public record ReviewResult(Outcome outcome, ReviewDTO review) {
        static ReviewResult denied(Outcome outcome) {
            return new ReviewResult(outcome, null);
        }
    }

    private ReviewDTO toDTO(Review review) {
        ReviewDTO dto = new ReviewDTO();
        dto.setId(review.getId());
        dto.setCreatedAt(review.getCreatedAt());
        dto.setNegotiationId(review.getNegotiation() != null ? review.getNegotiation().getId() : null);
        dto.setAuthorId(review.getAuthor() != null ? review.getAuthor().getId() : null);
        dto.setAuthorUsername(review.getAuthor() != null ? review.getAuthor().getUsername() : null);
        dto.setTargetId(review.getTarget() != null ? review.getTarget().getId() : null);
        dto.setRating(review.getRating());
        dto.setComment(review.getComment());
        if (review.getNegotiation() != null && review.getNegotiation().getProduct() != null) {
            dto.setProductTitle(review.getNegotiation().getProduct().getTitle());
        }
        return dto;
    }

    @Transactional(readOnly = true)
    public List<ReviewDTO> findForUser(Long targetId) {
        return repo.findByTargetIdOrderByCreatedAtDesc(targetId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * La moyenne d'un vendeur et le nombre d'avis qui la composent.
     *
     * <p>La moyenne est nulle — et non zero — tant qu'aucun avis n'existe: un
     * 0,0 se lirait comme la pire note possible.
     */
    @Transactional(readOnly = true)
    public ReviewSummaryDTO summaryFor(Long targetId) {
        ReviewSummaryDTO dto = new ReviewSummaryDTO();
        dto.setUserId(targetId);
        dto.setAverage(repo.averageRatingByTargetId(targetId));
        dto.setCount(repo.countByTargetId(targetId));
        return dto;
    }

    /**
     * Les transactions que l'appelant peut encore noter.
     *
     * <p>Sert a poser la question au bon moment — a l'ouverture de
     * l'application apres un rendez-vous — plutot qu'a esperer que l'acheteur
     * pense a revenir la poser lui-meme.
     */
    @Transactional(readOnly = true)
    public List<Long> pendingFor(Long buyerId) {
        return negotiationRepo.findBySenderId(buyerId).stream()
                .filter(n -> NegotiationStatus.STATUS_ACCEPTED.equalsIgnoreCase(n.getStatus()))
                .filter(n -> !repo.existsByNegotiationId(n.getId()))
                .map(Negotiation::getId)
                .collect(Collectors.toList());
    }

    @Transactional
    public ReviewResult create(Long negotiationId, Long authorId, Integer rating, String comment) {
        if (rating == null || rating < 1 || rating > 5) {
            return ReviewResult.denied(Outcome.INVALID_RATING);
        }

        Negotiation negotiation = negotiationRepo.findById(negotiationId).orElse(null);
        if (negotiation == null) {
            return ReviewResult.denied(Outcome.NOT_FOUND);
        }
        User buyer = negotiation.getSender();
        if (buyer == null || !buyer.getId().equals(authorId)) {
            return ReviewResult.denied(Outcome.NOT_THE_BUYER);
        }
        if (!NegotiationStatus.STATUS_ACCEPTED.equalsIgnoreCase(negotiation.getStatus())) {
            return ReviewResult.denied(Outcome.NOT_ACCEPTED);
        }
        if (repo.existsByNegotiationId(negotiationId)) {
            return ReviewResult.denied(Outcome.ALREADY_REVIEWED);
        }

        // Le vendeur est le destinataire de l'offre; pour les offres anterieures
        // a l'ajout de ce champ, le proprietaire de l'annonce en tient lieu.
        User seller = negotiation.getReceiver() != null
                ? negotiation.getReceiver()
                : (negotiation.getProduct() != null ? negotiation.getProduct().getUser() : null);
        if (seller == null) {
            return ReviewResult.denied(Outcome.NOT_FOUND);
        }

        Review review = Review.builder()
                .negotiation(negotiation)
                .author(buyer)
                .target(seller)
                .rating(rating)
                .comment(comment == null || comment.isBlank() ? null : comment.trim())
                .build();

        return new ReviewResult(Outcome.OK, toDTO(repo.save(review)));
    }
}
