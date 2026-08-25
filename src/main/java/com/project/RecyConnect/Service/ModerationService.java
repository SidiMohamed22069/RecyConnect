package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.BlockedUserDTO;
import com.project.RecyConnect.DTO.ReportDTO;
import com.project.RecyConnect.Model.*;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.ReportRepository;
import com.project.RecyConnect.Repository.UserBlockRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Blocages entre utilisateurs et signalements de contenu.
 *
 * <p>Les deux dispositifs que le reglement "Contenu genere par les
 * utilisateurs" de Google Play exige d'une application qui publie des annonces.
 * Ils vivent ensemble parce qu'ils repondent a la meme question — que faire
 * d'un contenu ou d'un compte qui pose probleme — et que le filtrage des
 * annonces les consulte tous les deux.
 */
@Service
public class ModerationService {

    /** Les motifs que la feuille de signalement de l'application propose. */
    public static final Set<String> ALLOWED_REASONS = Set.of(
            "PROHIBITED", "MISLEADING", "SCAM", "OFFENSIVE",
            "SPAM", "PERSONAL_DATA", "OTHER");

    private final UserBlockRepository blockRepo;
    private final ReportRepository reportRepo;
    private final UserRepo userRepo;
    private final ProductRepository productRepo;

    public ModerationService(UserBlockRepository blockRepo,
                             ReportRepository reportRepo,
                             UserRepo userRepo,
                             ProductRepository productRepo) {
        this.blockRepo = blockRepo;
        this.reportRepo = reportRepo;
        this.userRepo = userRepo;
        this.productRepo = productRepo;
    }

    // ------------------------------------------------------------------
    // Blocages
    // ------------------------------------------------------------------

    /**
     * Bloque {@code blockedId} pour {@code blocker}.
     *
     * <p>Idempotent : bloquer deux fois n'est pas une erreur, l'application
     * pouvant rejouer l'appel apres une coupure reseau.
     *
     * @throws IllegalArgumentException si la cible n'existe pas, ou si
     *         l'appelant tente de se bloquer lui-meme.
     */
    @Transactional
    public void block(User blocker, Long blockedId) {
        if (blockedId == null || blockedId.equals(blocker.getId())) {
            throw new IllegalArgumentException("Invalid target");
        }
        User blocked = userRepo.findById(blockedId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (blockRepo.existsByBlockerIdAndBlockedId(blocker.getId(), blockedId)) {
            return;
        }
        blockRepo.save(UserBlock.builder()
                .blocker(blocker)
                .blocked(blocked)
                .createdAt(OffsetDateTime.now())
                .build());
    }

    /** Leve le blocage. Ne rien avoir a lever n'est pas une erreur. */
    @Transactional
    public void unblock(User blocker, Long blockedId) {
        blockRepo.findByBlockerIdAndBlockedId(blocker.getId(), blockedId)
                .ifPresent(blockRepo::delete);
    }

    @Transactional(readOnly = true)
    public List<BlockedUserDTO> listBlocked(Long blockerId) {
        return blockRepo.findByBlockerIdOrderByCreatedAtDesc(blockerId).stream()
                .map(b -> new BlockedUserDTO(
                        b.getBlocked().getId(),
                        b.getBlocked().getUsername(),
                        b.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /**
     * Les comptes que {@code userId} ne doit plus voir — ceux qu'il a bloques
     * et ceux qui l'ont bloque.
     *
     * <p>Rend un ensemble vide pour un visiteur non authentifie : le catalogue
     * reste lisible sans compte, il n'y a alors personne a masquer.
     */
    @Transactional(readOnly = true)
    public Set<Long> hiddenUserIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return new HashSet<>(blockRepo.findHiddenUserIds(userId));
    }

    /** {@code true} si l'un des deux a bloque l'autre. */
    @Transactional(readOnly = true)
    public boolean isBlockedBetween(Long a, Long b) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        return blockRepo.existsByBlockerIdAndBlockedId(a, b)
                || blockRepo.existsByBlockerIdAndBlockedId(b, a);
    }

    // ------------------------------------------------------------------
    // Signalements
    // ------------------------------------------------------------------

    /**
     * Enregistre un signalement.
     *
     * <p>L'auteur vient du jeton, jamais du corps de la requete. Le proprietaire
     * du contenu est releve ici et non a la lecture : une annonce retiree entre
     * le signalement et son examen ne doit pas priver le moderateur de savoir
     * qui elle visait.
     *
     * @throws IllegalArgumentException si le motif, le type de cible ou la
     *         cible elle-meme sont invalides.
     */
    @Transactional
    public ReportDTO report(User reporter, ReportDTO request) {
        String targetType = request.getTargetType() == null
                ? "" : request.getTargetType().trim().toUpperCase();
        String reason = request.getReason() == null
                ? "" : request.getReason().trim().toUpperCase();

        if (!ALLOWED_REASONS.contains(reason)) {
            throw new IllegalArgumentException("Unknown reason");
        }
        if (request.getTargetId() == null) {
            throw new IllegalArgumentException("Target is required");
        }

        Long targetUserId;
        if (Report.TARGET_PRODUCT.equals(targetType)) {
            Product product = productRepo.findById(request.getTargetId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));
            targetUserId = product.getUser() != null ? product.getUser().getId() : null;
        } else if (Report.TARGET_USER.equals(targetType)) {
            User target = userRepo.findById(request.getTargetId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            targetUserId = target.getId();
        } else {
            throw new IllegalArgumentException("Unknown target type");
        }

        if (reporter.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("You cannot report your own content");
        }

        // Un meme contenu signale deux fois par la meme personne n'ajoute rien
        // a la file de moderation tant que le premier signalement attend.
        if (reportRepo.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
                reporter.getId(), targetType, request.getTargetId(), ReportStatus.PENDING)) {
            return reportRepo
                    .findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, request.getTargetId())
                    .stream()
                    .filter(r -> r.getReporter() != null && reporter.getId().equals(r.getReporter().getId()))
                    .findFirst()
                    .map(this::toDTO)
                    .orElseThrow(() -> new IllegalStateException("Report not found"));
        }

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(targetType)
                .targetId(request.getTargetId())
                .targetUserId(targetUserId)
                .reason(reason)
                .details(trimToNull(request.getDetails()))
                .status(ReportStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();

        return toDTO(reportRepo.save(report));
    }

    @Transactional(readOnly = true)
    public List<ReportDTO> listReports(ReportStatus status) {
        List<Report> reports = status == null
                ? reportRepo.findAllByOrderByCreatedAtDesc()
                : reportRepo.findByStatusOrderByCreatedAtAsc(status);
        return reports.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return reportRepo.countByStatus(ReportStatus.PENDING);
    }

    /**
     * Consigne le traitement d'un signalement : qui l'a traite, quand, et ce
     * qui a ete decide. C'est la tracabilite que Play attend d'une moderation.
     */
    @Transactional
    public ReportDTO handle(Long reportId, ReportStatus status, String resolution, User moderator) {
        Report report = reportRepo.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found"));

        report.setStatus(status);
        report.setResolution(trimToNull(resolution));
        report.setHandledBy(moderator);
        report.setHandledAt(status == ReportStatus.PENDING ? null : OffsetDateTime.now());

        return toDTO(reportRepo.save(report));
    }

    // ------------------------------------------------------------------

    private ReportDTO toDTO(Report report) {
        ReportDTO dto = new ReportDTO();
        dto.setId(report.getId());
        if (report.getReporter() != null) {
            dto.setReporterId(report.getReporter().getId());
            dto.setReporterName(report.getReporter().getUsername());
        }
        dto.setTargetType(report.getTargetType());
        dto.setTargetId(report.getTargetId());
        dto.setTargetUserId(report.getTargetUserId());
        if (report.getTargetUserId() != null) {
            userRepo.findById(report.getTargetUserId())
                    .ifPresent(u -> dto.setTargetUserName(u.getUsername()));
        }
        dto.setReason(report.getReason());
        dto.setDetails(report.getDetails());
        dto.setStatus(report.getStatus());
        dto.setCreatedAt(report.getCreatedAt());
        dto.setHandledAt(report.getHandledAt());
        if (report.getHandledBy() != null) {
            dto.setHandledByName(report.getHandledBy().getUsername());
        }
        dto.setResolution(report.getResolution());
        return dto;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
