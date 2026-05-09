package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.EarningsDTO;
import com.project.RecyConnect.DTO.NegotiationDTO;
import com.project.RecyConnect.Model.Negotiation;
import com.project.RecyConnect.Model.NegotiationStatus;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.User;
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

    public NegotiationService(NegotiationRepository repo, UserRepo userRepo,
                              ProductRepository productRepo, NotificationService notificationService) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.productRepo = productRepo;
        this.notificationService = notificationService;
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
            dto.setProductImageUrls(n.getProduct().getImageUrls());
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

    public List<NegotiationDTO> findAll() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public Optional<NegotiationDTO> findById(Long id) {
        return repo.findById(id).map(this::toDTO);
    }

    public List<NegotiationDTO> findBySenderId(Long senderId) {
        return repo.findBySenderId(senderId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<NegotiationDTO> findByReceiverId(Long receiverId) {
        return repo.findByReceiverId(receiverId).stream().map(this::toDTO).collect(Collectors.toList());
    }

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
        NegotiationDTO saved = toDTO(savedEntity);

        notificationService.sendOfferNotification(
                saved.getReceiverId(),
                saved.getSenderId(),
                saved.getId(),
                saved.getProductTitle() != null ? saved.getProductTitle() : "un produit"
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

            notificationService.sendNegotiationNotification(
                    updated.getReceiver().getId(),
                    updated.getSender().getId(),
                    updated.getId(),
                    "OFFER_UPDATED",
                    "Offre modifiee",
                    updated.getSender().getUsername() + " a modifie une offre sur " + updated.getProduct().getTitle()
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
                        updated.getProductTitle() != null ? updated.getProductTitle() : "un produit"
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

        notificationService.sendNegotiationNotification(
                saved.getReceiver().getId(),
                saved.getSender().getId(),
                saved.getId(),
                "OFFER_CANCELLED",
                "Offre annulee",
                saved.getSender().getUsername() + " a annule son offre sur " + saved.getProduct().getTitle()
        );

        notifyQueueUpdated(saved.getProduct().getId(), saved.getReceiver().getId(), saved.getSender().getId());
        return toDTO(saved);
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

        notificationService.sendNegotiationNotification(
                saved.getSender().getId(),
                saved.getReceiver().getId(),
                saved.getId(),
                "OFFER_REJECTED",
                "Offre rejetee",
                "Votre offre sur " + saved.getProduct().getTitle() + " a ete rejetee"
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
            product.setStatus("recycled");
        }
        productRepo.save(product);

        offer.setStatus(NegotiationStatus.STATUS_ACCEPTED);
        Negotiation accepted = repo.save(offer);

        notificationService.sendNegotiationNotification(
                accepted.getSender().getId(),
                accepted.getReceiver().getId(),
                accepted.getId(),
                "OFFER_ACCEPTED",
                "Offre acceptee",
                "Votre offre sur " + accepted.getProduct().getTitle() + " a ete acceptee"
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

    public EarningsDTO getSellerEarnings(Long sellerId) {
        Double amount = repo.sumAcceptedAmountBySellerId(sellerId);
        Long acceptedCount = repo.countAcceptedBySellerId(sellerId);

        EarningsDTO dto = new EarningsDTO();
        dto.setUserId(sellerId);
        dto.setTotalAmount(amount != null ? amount : 0.0);
        dto.setAcceptedOffersCount(acceptedCount != null ? acceptedCount : 0L);
        return dto;
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }

    private void cancelIncompatibleOffers(Long productId, Long availableQuantity, Long actorUserId) {
        List<Negotiation> pendings = repo.findByProductIdAndStatusIn(productId, List.of(NegotiationStatus.STATUS_PENDING));
        for (Negotiation n : pendings) {
            if (n.getQuantity() != null && n.getQuantity() > availableQuantity) {
            n.setStatus(NegotiationStatus.STATUS_AUTO_CANCELLED_STOCK);
                repo.save(n);

                notificationService.sendNegotiationNotification(
                        n.getSender().getId(),
                        actorUserId,
                        n.getId(),
                        "OFFER_AUTO_CANCELLED_STOCK",
                        "Offre annulee automatiquement",
                        "Votre offre sur " + n.getProduct().getTitle() + " depasse la quantite restante"
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
                notificationService.sendNegotiationNotification(
                        dto.getSenderId(),
                        changedOffer.getSender().getId(),
                        dto.getId(),
                        "OUTBID_BY_BETTER_OFFER",
                        "Offre plus rentable detectee",
                        "Une offre plus rentable que la votre est en tete de file pour " + changedOffer.getProduct().getTitle()
                );
            }
        }
    }

    private void notifyQueueUpdated(Long productId, Long sellerId, Long buyerId) {
        Product product = productRepo.findById(productId).orElse(null);
        String productTitle = product != null ? product.getTitle() : "ce produit";

        if (sellerId != null) {
            notificationService.sendNegotiationNotification(
                    sellerId,
                    buyerId,
                    null,
                    "QUEUE_UPDATED",
                    "File des offres mise a jour",
                    "La file des offres a ete mise a jour pour " + productTitle
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
