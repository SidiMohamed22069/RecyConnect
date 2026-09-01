package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.EarningsDTO;
import com.project.RecyConnect.DTO.NegotiationContactDTO;
import com.project.RecyConnect.DTO.NegotiationDTO;
import com.project.RecyConnect.DTO.NegotiationHistoryDTO;
import com.project.RecyConnect.DTO.TransactionDTO;
import com.project.RecyConnect.Model.Negotiation;
import com.project.RecyConnect.Model.NegotiationHistory;
import com.project.RecyConnect.Model.NegotiationStatus;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.ProductStatus;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.NegotiationHistoryRepository;
import com.project.RecyConnect.Repository.NegotiationRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class NegotiationService {
    private final NegotiationRepository repo;
    private final UserRepo userRepo;
    private final ProductRepository productRepo;
    private final NotificationService notificationService;
    private final FileUrlService fileUrlService;
    private final NegotiationHistoryRepository historyRepo;

    public NegotiationService(NegotiationRepository repo, UserRepo userRepo,
                              ProductRepository productRepo, NotificationService notificationService,
                              FileUrlService fileUrlService,
                              NegotiationHistoryRepository historyRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.productRepo = productRepo;
        this.notificationService = notificationService;
        this.fileUrlService = fileUrlService;
        this.historyRepo = historyRepo;
    }

    private NegotiationDTO toDTO(Negotiation n) {
        NegotiationDTO dto = new NegotiationDTO();
        dto.setId(n.getId());
        dto.setCreatedAt(n.getCreatedAt());
        dto.setSenderId(n.getSender() != null ? n.getSender().getId() : null);
        dto.setReceiverId(n.getReceiver() != null ? n.getReceiver().getId() : null);
        dto.setProductId(n.getProduct() != null ? n.getProduct().getId() : null);
        dto.setStatus(n.getStatus());
        dto.setPrice(n.getPrice());
        dto.setQuantity(n.getQuantity());
        dto.setTotalAmount(calculateTotalAmount(n));
        dto.setSenderUsername(n.getSender() != null ? n.getSender().getUsername() : null);
        dto.setReceiverUsername(n.getReceiver() != null ? n.getReceiver().getUsername() : null);
        if (n.getProduct() != null) {
            dto.setProductTitle(n.getProduct().getTitle());
            dto.setProductImageUrls(fileUrlService.toPublicUrls(n.getProduct().getImageUrls()));
            dto.setProductUnit(n.getProduct().getUnit());
        }
        return dto;
    }

    private Negotiation fromDTO(NegotiationDTO dto) {
        Negotiation n = new Negotiation();
        n.setId(dto.getId());
        n.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : OffsetDateTime.now());
        n.setStatus(dto.getStatus() != null ? dto.getStatus().toLowerCase() : NegotiationStatus.STATUS_PENDING);
        n.setPrice(dto.getPrice());
        n.setQuantity(dto.getQuantity());
        if (dto.getSenderId() != null) {
            userRepo.findById(dto.getSenderId()).ifPresent(n::setSender);
        }
        if (dto.getReceiverId() != null) {
            userRepo.findById(dto.getReceiverId()).ifPresent(n::setReceiver);
        }
        if (dto.getProductId() != null) {
            productRepo.findById(dto.getProductId()).ifPresent(n::setProduct);
        }
        return n;
    }

    @Transactional(readOnly = true)
    public List<NegotiationDTO> findAll() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<NegotiationDTO> findById(Long id) {
        return repo.findById(id).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public List<NegotiationDTO> findBySenderId(Long senderId) {
        return repo.findBySenderId(senderId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NegotiationDTO> findByReceiverId(Long receiverId) {
        return repo.findByReceiverId(receiverId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NegotiationDTO> findByProductId(Long productId, String status) {
        List<NegotiationDTO> rows = repo.findByProductId(productId).stream()
                .filter(n -> status == null || status.isEmpty() || status.equalsIgnoreCase(n.getStatus()))
                .map(this::toDTO)
                .collect(Collectors.toList());

        if (status == null || status.isEmpty() || NegotiationStatus.STATUS_PENDING.equalsIgnoreCase(status)) {
            return sortAndRank(rows);
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<NegotiationDTO> getQueueByProductId(Long productId) {
        List<NegotiationDTO> queue = repo.findByProductIdAndStatusIn(productId, List.of(NegotiationStatus.STATUS_PENDING)).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return sortAndRank(queue);
    }

    @Transactional
    public NegotiationDTO save(NegotiationDTO dto) {
        Negotiation offer = fromDTO(dto);

        if (offer.getProduct() == null) {
            throw new RuntimeException("Product is required");
        }
        if (offer.getSender() == null) {
            throw new RuntimeException("Sender is required");
        }
        if (offer.getQuantity() == null || offer.getQuantity() <= 0) {
            throw new RuntimeException("Quantity must be greater than 0");
        }
        if (offer.getPrice() == null || offer.getPrice() <= 0) {
            throw new RuntimeException("Unit price must be greater than 0");
        }

        Product product = productRepo.findById(offer.getProduct().getId())
                .orElseThrow(() -> new RuntimeException("Product not found"));
        User sender = userRepo.findById(offer.getSender().getId())
                .orElseThrow(() -> new RuntimeException("Sender not found"));
        User receiver = product.getUser();

        if (receiver == null) {
            throw new RuntimeException("Product owner not found");
        }
        if (receiver.getId().equals(sender.getId())) {
            throw new RuntimeException("You cannot negotiate on your own product");
        }

        offer.setProduct(product);
        offer.setSender(sender);
        offer.setReceiver(receiver);
        offer.setStatus(NegotiationStatus.STATUS_PENDING);

        Negotiation savedEntity = repo.save(offer);
        recordHistory(savedEntity, sender, "OFFER");
        NegotiationDTO saved = toDTO(savedEntity);

        notificationService.sendOfferNotification(
                saved.getReceiverId(),
                saved.getSenderId(),
                saved.getId(),
                saved.getProductTitle()
        );

        notifyOutbidUsers(savedEntity);
        notifyQueueUpdated(savedEntity.getProduct().getId(), savedEntity.getReceiver().getId(), savedEntity.getSender().getId());

        return saved;
    }

    @Transactional
    public NegotiationDTO update(Long id, NegotiationDTO dto) {
        return repo.findById(id).map(existing -> {
            if (!NegotiationStatus.STATUS_PENDING.equalsIgnoreCase(existing.getStatus())) {
                throw new RuntimeException("Only pending offers can be updated");
            }
            if (dto.getPrice() != null) {
                if (dto.getPrice() <= 0) throw new RuntimeException("Unit price must be greater than 0");
                existing.setPrice(dto.getPrice());
            }
            if (dto.getQuantity() != null) {
                if (dto.getQuantity() <= 0) throw new RuntimeException("Quantity must be greater than 0");
                existing.setQuantity(dto.getQuantity());
            }
            Negotiation updated = repo.save(existing);
            recordHistory(updated, updated.getSender(), "UPDATED");

            notificationService.sendLocalizedNotification(
                    updated.getReceiver().getId(),
                    updated.getSender().getId(),
                    updated.getId(),
                    "OFFER_UPDATED",
                    updated.getSender().getUsername(),
                    updated.getProduct().getTitle()
            );

            notifyOutbidUsers(updated);
            notifyQueueUpdated(updated.getProduct().getId(), updated.getReceiver().getId(), updated.getSender().getId());

            return toDTO(updated);
        }).orElseThrow(() -> new RuntimeException("Negotiation not found"));
    }

    @Transactional
    public NegotiationDTO patch(Long id, NegotiationDTO dto) {
        return repo.findById(id).map(existing -> {
            String oldStatus = existing.getStatus();

            if (dto.getStatus() != null) existing.setStatus(dto.getStatus());
            if (dto.getPrice() != null) existing.setPrice(dto.getPrice());
            if (dto.getQuantity() != null) existing.setQuantity(dto.getQuantity());
            if (dto.getSenderId() != null)
                userRepo.findById(dto.getSenderId()).ifPresent(existing::setSender);
            if (dto.getReceiverId() != null)
                userRepo.findById(dto.getReceiverId()).ifPresent(existing::setReceiver);
            if (dto.getProductId() != null)
                productRepo.findById(dto.getProductId()).ifPresent(existing::setProduct);

            NegotiationDTO updated = toDTO(repo.save(existing));

            if ("refused".equalsIgnoreCase(updated.getStatus())
                    && !"refused".equalsIgnoreCase(oldStatus)
                    && updated.getSenderId() != null
                    && updated.getReceiverId() != null) {

                notificationService.sendRefusalNotification(
                        updated.getSenderId(),
                        updated.getReceiverId(),
                        updated.getId(),
                        updated.getProductTitle()
                );
            }

            return updated;
        }).orElseThrow(() -> new RuntimeException("Negotiation not found"));
    }

    @Transactional
    public NegotiationDTO cancelByBuyer(Long negotiationId, Long buyerId) {
        Negotiation offer = repo.findById(negotiationId)
                .orElseThrow(() -> new RuntimeException("Negotiation not found"));

        if (!offer.getSender().getId().equals(buyerId)) {
            throw new RuntimeException("Only offer buyer can cancel this offer");
        }
        if (!NegotiationStatus.STATUS_PENDING.equalsIgnoreCase(offer.getStatus())) {
            throw new RuntimeException("Only pending offers can be cancelled");
        }

        offer.setStatus(NegotiationStatus.STATUS_CANCELLED);
        Negotiation saved = repo.save(offer);

        notificationService.sendLocalizedNotification(
                saved.getReceiver().getId(),
                saved.getSender().getId(),
                saved.getId(),
                "OFFER_CANCELLED",
                saved.getSender().getUsername(),
                saved.getProduct().getTitle()
        );

        notifyQueueUpdated(saved.getProduct().getId(), saved.getReceiver().getId(), saved.getSender().getId());
        return toDTO(saved);
    }

    /**
     * La contre-proposition du vendeur: "pas a ce prix-la, mais a celui-ci".
     *
     * <p>C'est la troisieme reponse d'une negociation reelle, et la plus
     * frequente; le vendeur n'avait jusqu'ici que deux boutons, accepter ou
     * refuser — c'est-a-dire perdre l'acheteur pour deux ouguiyas d'ecart.
     *
     * <p>L'offre reste en attente et change de main: c'est desormais a
     * l'acheteur de repondre. Le fil d'historique conserve les deux montants,
     * sans quoi la contre-proposition effacerait l'offre d'origine.
     */
    @Transactional
    public NegotiationDTO counterBySeller(Long negotiationId, Long sellerId,
                                          Double price, Integer quantity) {
        Negotiation offer = repo.findById(negotiationId)
                .orElseThrow(() -> new RuntimeException("Negotiation not found"));

        User seller = sellerOf(offer);
        if (seller == null || !seller.getId().equals(sellerId)) {
            throw new RuntimeException("Only product owner can counter this offer");
        }
        if (!NegotiationStatus.STATUS_PENDING.equalsIgnoreCase(offer.getStatus())) {
            throw new RuntimeException("Only pending offers can be countered");
        }
        if (price != null && price <= 0) {
            throw new RuntimeException("Unit price must be greater than 0");
        }
        if (quantity != null && quantity <= 0) {
            throw new RuntimeException("Quantity must be greater than 0");
        }
        if (price == null && quantity == null) {
            throw new RuntimeException("A counter-offer must change the price or the quantity");
        }

        // Une contre-proposition ne peut pas porter sur plus que le stock
        // restant: elle serait annulee d'office a la premiere revision de
        // quantite, et l'acheteur aurait accepte une offre morte.
        Product product = offer.getProduct();
        long available = product != null && product.getQuantityAvailable() != null
                ? product.getQuantityAvailable() : 0L;
        int newQuantity = quantity != null ? quantity
                : (offer.getQuantity() != null ? offer.getQuantity() : 0);
        if (newQuantity > available) {
            throw new RuntimeException("Counter-offer quantity exceeds remaining stock");
        }

        if (price != null) offer.setPrice(price);
        if (quantity != null) offer.setQuantity(quantity);
        Negotiation countered = repo.save(offer);
        recordHistory(countered, seller, "COUNTER_OFFER");

        notificationService.sendLocalizedNotification(
                countered.getSender().getId(),
                seller.getId(),
                countered.getId(),
                "OFFER_COUNTERED",
                seller.getUsername(),
                countered.getPrice(),
                countered.getProduct().getTitle()
        );

        return toDTO(countered);
    }

    /** Le fil d'une negociation, du plus ancien tour au plus recent. */
    @Transactional(readOnly = true)
    public List<NegotiationHistoryDTO> historyOf(Long negotiationId) {
        return historyRepo.findByNegotiationIdOrderByCreatedAtAsc(negotiationId).stream()
                .map(entry -> {
                    NegotiationHistoryDTO dto = new NegotiationHistoryDTO();
                    dto.setId(entry.getId());
                    dto.setCreatedAt(entry.getCreatedAt());
                    dto.setNegotiationId(negotiationId);
                    dto.setAuthorId(entry.getAuthor() != null ? entry.getAuthor().getId() : null);
                    dto.setAuthorUsername(entry.getAuthor() != null ? entry.getAuthor().getUsername() : null);
                    dto.setKind(entry.getKind());
                    dto.setPrice(entry.getPrice());
                    dto.setQuantity(entry.getQuantity());
                    dto.setTotalAmount(entry.getPrice() != null && entry.getQuantity() != null
                            ? entry.getPrice() * entry.getQuantity() : null);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Le journal des transactions conclues d'un utilisateur, achats et ventes
     * confondus.
     *
     * <p>Le total des gains etait deja affiche; ce qui manquait, c'est le
     * detail qui en fait un outil de gestion — quelle date, quel lot, quelle
     * contrepartie, quelle quantite.
     */
    @Transactional(readOnly = true)
    public List<TransactionDTO> transactionsFor(Long userId) {
        return repo.findAcceptedForUser(userId).stream()
                .map(n -> {
                    User buyer = n.getSender();
                    User seller = sellerOf(n);
                    boolean isSeller = seller != null && seller.getId().equals(userId);
                    User counterpart = isSeller ? buyer : seller;

                    TransactionDTO dto = new TransactionDTO();
                    dto.setNegotiationId(n.getId());
                    dto.setDate(n.getCreatedAt());
                    dto.setRole(isSeller ? "SELLER" : "BUYER");
                    if (n.getProduct() != null) {
                        dto.setProductId(n.getProduct().getId());
                        dto.setProductTitle(n.getProduct().getTitle());
                        dto.setProductUnit(n.getProduct().getUnit());
                    }
                    if (counterpart != null) {
                        dto.setCounterpartId(counterpart.getId());
                        dto.setCounterpartUsername(counterpart.getUsername());
                    }
                    dto.setQuantity(n.getQuantity());
                    dto.setUnitPrice(n.getPrice());
                    dto.setTotalAmount(calculateTotalAmount(n));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Ajoute un tour au fil de la negociation.
     *
     * <p>Un fil incomplet vaut mieux qu'une negociation perdue: l'echec
     * d'ecriture est avale, il ne doit pas annuler l'offre elle-meme.
     */
    private void recordHistory(Negotiation negotiation, User author, String kind) {
        try {
            historyRepo.save(NegotiationHistory.builder()
                    .negotiation(negotiation)
                    .author(author)
                    .kind(kind)
                    .price(negotiation.getPrice())
                    .quantity(negotiation.getQuantity())
                    .build());
        } catch (RuntimeException e) {
            // Le fil est un confort d'affichage, pas la source de verite.
        }
    }

    @Transactional
    public NegotiationDTO rejectBySeller(Long negotiationId, Long sellerId) {
        Negotiation offer = repo.findById(negotiationId)
                .orElseThrow(() -> new RuntimeException("Negotiation not found"));

        if (!offer.getProduct().getUser().getId().equals(sellerId)) {
            throw new RuntimeException("Only product owner can reject this offer");
        }
        if (!NegotiationStatus.STATUS_PENDING.equalsIgnoreCase(offer.getStatus())) {
            throw new RuntimeException("Only pending offers can be rejected");
        }

        offer.setStatus(NegotiationStatus.STATUS_REJECTED);
        Negotiation saved = repo.save(offer);

        notificationService.sendLocalizedNotification(
                saved.getSender().getId(),
                saved.getReceiver().getId(),
                saved.getId(),
                "OFFER_REJECTED",
                saved.getProduct().getTitle()
        );

        notifyQueueUpdated(saved.getProduct().getId(), saved.getReceiver().getId(), saved.getSender().getId());
        return toDTO(saved);
    }

    @Transactional
    public NegotiationDTO acceptBySeller(Long negotiationId, Long sellerId) {
        Negotiation offer = repo.findById(negotiationId)
                .orElseThrow(() -> new RuntimeException("Negotiation not found"));

        if (!offer.getProduct().getUser().getId().equals(sellerId)) {
            throw new RuntimeException("Only product owner can accept this offer");
        }
        if (!NegotiationStatus.STATUS_PENDING.equalsIgnoreCase(offer.getStatus())) {
            throw new RuntimeException("Only pending offers can be accepted");
        }

        Product product = productRepo.findByIdForUpdate(offer.getProduct().getId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        long available = product.getQuantityAvailable() != null ? product.getQuantityAvailable() : 0L;
        long requested = offer.getQuantity() != null ? offer.getQuantity() : 0L;
        if (requested <= 0L) {
            throw new RuntimeException("Invalid offer quantity");
        }
        if (requested > available) {
            throw new RuntimeException("Offer quantity exceeds remaining stock");
        }

        product.setQuantityAvailable(available - requested);
        if (product.getQuantityAvailable() <= 0L) {
            product.setStatus(ProductStatus.RECYCLED);
        }
        productRepo.save(product);

        offer.setStatus(NegotiationStatus.STATUS_ACCEPTED);
        Negotiation accepted = repo.save(offer);

        notificationService.sendLocalizedNotification(
                accepted.getSender().getId(),
                accepted.getReceiver().getId(),
                accepted.getId(),
                "OFFER_ACCEPTED",
                accepted.getProduct().getTitle()
        );

        cancelIncompatibleOffers(accepted.getProduct().getId(), product.getQuantityAvailable(), accepted.getReceiver().getId());
        notifyQueueUpdated(accepted.getProduct().getId(), accepted.getReceiver().getId(), accepted.getSender().getId());

        return toDTO(accepted);
    }

    @Transactional
    public void onProductStockChanged(Long productId, Long changedByUserId) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        long available = product.getQuantityAvailable() != null ? product.getQuantityAvailable() : 0L;
        cancelIncompatibleOffers(productId, available, changedByUserId);
        notifyQueueUpdated(productId, changedByUserId, null);
    }

    @Transactional(readOnly = true)
    public EarningsDTO getSellerEarnings(Long sellerId) {
        Double amount = repo.sumAcceptedAmountBySellerId(sellerId);
        Long acceptedCount = repo.countAcceptedBySellerId(sellerId);

        EarningsDTO dto = new EarningsDTO();
        dto.setUserId(sellerId);
        dto.setTotalAmount(amount != null ? amount : 0.0);
        dto.setAcceptedOffersCount(acceptedCount != null ? acceptedCount : 0L);
        return dto;
    }

    // ------------------------------------------------------------------
    // Mise en relation (C3 de l'audit)

    /**
     * Ce que le serveur consent a reveler, selon qui demande.
     *
     * <p>Les quatre cas se traduisent par quatre codes HTTP distincts : le
     * client doit pouvoir distinguer "offre inconnue" de "ce n'est pas la
     * votre" et de "pas encore acceptee", sans avoir a lire un message.
     */
    public enum ContactAccess {
        GRANTED,
        NOT_FOUND,
        NOT_A_PARTY,
        NOT_ACCEPTED
    }

    /** Resultat de {@link #findContact(Long, Long)} : le verdict, et la fiche s'il est favorable. */
    public record ContactLookup(ContactAccess access, NegotiationContactDTO contact) {
        static ContactLookup denied(ContactAccess access) {
            return new ContactLookup(access, null);
        }
    }

    /**
     * Les numeros des deux parties d'une offre, si {@code requesterId} y a droit.
     *
     * <p>Trois verrous, dans cet ordre :
     * <ol>
     *   <li>l'offre existe ;</li>
     *   <li>le demandeur en est l'acheteur ou le vendeur — un tiers authentifie
     *       n'obtient rien, c'est tout l'objet du point C3 ;</li>
     *   <li>elle est acceptee — un numero n'est pas la contrepartie d'une offre
     *       simplement deposee, sans quoi il suffirait d'offrir un ouguiya sur
     *       chaque annonce pour recolter l'annuaire.</li>
     * </ol>
     *
     * <p>Les deux numeros sont rendus, pas seulement celui d'en face : le
     * demandeur connait deja le sien, et l'application s'en sert pour prevenir
     * chaque partie du numero de l'autre en une seule lecture.
     */
    @Transactional(readOnly = true)
    public ContactLookup findContact(Long negotiationId, Long requesterId) {
        Negotiation offer = repo.findById(negotiationId).orElse(null);
        if (offer == null) {
            return ContactLookup.denied(ContactAccess.NOT_FOUND);
        }

        User buyer = offer.getSender();
        User seller = sellerOf(offer);

        if (!isParty(requesterId, buyer) && !isParty(requesterId, seller)) {
            return ContactLookup.denied(ContactAccess.NOT_A_PARTY);
        }
        if (!NegotiationStatus.STATUS_ACCEPTED.equalsIgnoreCase(offer.getStatus())) {
            return ContactLookup.denied(ContactAccess.NOT_ACCEPTED);
        }

        NegotiationContactDTO dto = new NegotiationContactDTO();
        dto.setNegotiationId(offer.getId());
        dto.setStatus(offer.getStatus());
        if (buyer != null) {
            dto.setBuyerId(buyer.getId());
            dto.setBuyerUsername(buyer.getUsername());
            dto.setBuyerPhone(phoneToString(buyer.getPhone()));
        }
        if (seller != null) {
            dto.setSellerId(seller.getId());
            dto.setSellerUsername(seller.getUsername());
            dto.setSellerPhone(phoneToString(seller.getPhone()));
        }
        return new ContactLookup(ContactAccess.GRANTED, dto);
    }

    /**
     * Le vendeur d'une offre : son destinataire, ou a defaut le proprietaire de
     * l'annonce.
     *
     * <p>Les offres anterieures a l'ajout de {@code receiver} n'ont pas de
     * destinataire enregistre. Sans ce repli, leur vendeur serait vu comme un
     * tiers et se verrait refuser le numero de son propre acheteur.
     */
    private User sellerOf(Negotiation offer) {
        if (offer.getReceiver() != null) {
            return offer.getReceiver();
        }
        return offer.getProduct() != null ? offer.getProduct().getUser() : null;
    }

    private boolean isParty(Long requesterId, User party) {
        return requesterId != null && party != null && requesterId.equals(party.getId());
    }

    private String phoneToString(Long phone) {
        return phone != null ? String.valueOf(phone) : null;
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }

    private void cancelIncompatibleOffers(Long productId, Long availableQuantity, Long actorUserId) {
        List<Negotiation> pendings = repo.findByProductIdAndStatusIn(productId, List.of(NegotiationStatus.STATUS_PENDING));
        for (Negotiation n : pendings) {
            if (n.getQuantity() != null && n.getQuantity() > availableQuantity) {
            n.setStatus(NegotiationStatus.STATUS_AUTO_CANCELLED_STOCK);
                repo.save(n);

                notificationService.sendLocalizedNotification(
                        n.getSender().getId(),
                        actorUserId,
                        n.getId(),
                        "OFFER_AUTO_CANCELLED_STOCK",
                        n.getProduct().getTitle()
                );
            }
        }
    }

    private void notifyOutbidUsers(Negotiation changedOffer) {
        List<NegotiationDTO> queue = getQueueByProductId(changedOffer.getProduct().getId());
        double changedTotal = calculateTotalAmount(changedOffer);

        for (NegotiationDTO dto : queue) {
            if (dto.getId().equals(changedOffer.getId())) {
                continue;
            }
            double total = dto.getTotalAmount() != null ? dto.getTotalAmount() : 0.0;
            if (changedTotal > total) {
                notificationService.sendLocalizedNotification(
                        dto.getSenderId(),
                        changedOffer.getSender().getId(),
                        dto.getId(),
                        "OUTBID_BY_BETTER_OFFER",
                        changedOffer.getProduct().getTitle()
                );
            }
        }
    }

    private void notifyQueueUpdated(Long productId, Long sellerId, Long buyerId) {
        Product product = productRepo.findById(productId).orElse(null);
        // Aucun libelle de repli en dur ici: un titre absent est remplace par
        // NotificationMessages, seul a savoir dans quelle langue le faire.
        String productTitle = product != null ? product.getTitle() : null;

        if (sellerId != null) {
            notificationService.sendLocalizedNotification(
                    sellerId,
                    buyerId,
                    null,
                    "QUEUE_UPDATED",
                    productTitle
            );
        }
    }

    private List<NegotiationDTO> sortAndRank(List<NegotiationDTO> rows) {
        rows.sort(Comparator
                .comparing((NegotiationDTO n) -> n.getTotalAmount() != null ? n.getTotalAmount() : 0.0).reversed()
                .thenComparing((NegotiationDTO n) -> n.getPrice() != null ? n.getPrice() : 0.0, Comparator.reverseOrder())
                .thenComparing(NegotiationDTO::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

        int rank = 1;
        for (NegotiationDTO row : rows) {
            row.setPriorityRank(rank++);
        }
        return rows;
    }

    private Double calculateTotalAmount(Negotiation n) {
        if (n.getPrice() == null || n.getQuantity() == null) {
            return 0.0;
        }
        return n.getPrice() * n.getQuantity();
    }
}
