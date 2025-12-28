package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.NegotiationDTO;
import com.project.RecyConnect.Model.Negotiation;
import com.project.RecyConnect.Repository.NegotiationRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.springframework.stereotype.Service;

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
        // Nested info
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
        n.setCreatedAt(dto.getCreatedAt());
        n.setStatus(dto.getStatus());
        n.setPrice(dto.getPrice());
        n.setQuantity(dto.getQuantity());
        if (dto.getSenderId() != null)
            userRepo.findById(dto.getSenderId()).ifPresent(n::setSender);
        if (dto.getReceiverId() != null)
            userRepo.findById(dto.getReceiverId()).ifPresent(n::setReceiver);
        if (dto.getProductId() != null)
            productRepo.findById(dto.getProductId()).ifPresent(n::setProduct);
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
        return repo.findByProductId(productId).stream()
                .filter(n -> status == null || status.isEmpty() || status.equalsIgnoreCase(n.getStatus()))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public NegotiationDTO save(NegotiationDTO dto) {
        NegotiationDTO saved = toDTO(repo.save(fromDTO(dto)));
        
        // 🔔 Notification: Nouvelle offre reçue
        if (saved.getReceiverId() != null && saved.getSenderId() != null) {
            notificationService.sendOfferNotification(
                saved.getReceiverId(),  // User qui reçoit l'offre
                saved.getSenderId(),    // User qui envoie
                saved.getId(),          // ID de la négociation
                saved.getProductTitle() != null ? saved.getProductTitle() : "un produit" // Info du produit
            );
        }
        
        return saved;
    }

    public NegotiationDTO update(Long id, NegotiationDTO dto) {
        return repo.findById(id).map(existing -> {
            existing.setStatus(dto.getStatus());
            existing.setPrice(dto.getPrice());
            existing.setQuantity(dto.getQuantity());
            return toDTO(repo.save(existing));
        }).orElseThrow(() -> new RuntimeException("Negotiation not found"));
    }

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
            
            // 🔔 Notification: Offre refusée
            if ("refused".equalsIgnoreCase(updated.getStatus()) 
                && !"refused".equalsIgnoreCase(oldStatus)
                && updated.getSenderId() != null 
                && updated.getReceiverId() != null) {
                
                notificationService.sendRefusalNotification(
                    updated.getSenderId(),    // User qui a envoyé l'offre
                    updated.getReceiverId(),  // User qui refuse
                    updated.getId(),
                    updated.getProductTitle() != null ? updated.getProductTitle() : "un produit"
                );
            }
            
            return updated;
        }).orElseThrow(() -> new RuntimeException("Negotiation not found"));
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }
}
