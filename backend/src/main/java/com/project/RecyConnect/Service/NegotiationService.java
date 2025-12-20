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

    public NegotiationService(NegotiationRepository repo, UserRepo userRepo, ProductRepository productRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.productRepo = productRepo;
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

    public NegotiationDTO save(NegotiationDTO dto) {
        return toDTO(repo.save(fromDTO(dto)));
    }

    public NegotiationDTO update(Long id, NegotiationDTO dto) {
        return repo.findById(id).map(existing -> {
            existing.setStatus(dto.getStatus());
            existing.setPrice(dto.getPrice());
            existing.setQuantity(dto.getQuantity());
            return toDTO(repo.save(existing));
        }).orElseThrow(() -> new RuntimeException("Negotiation not found"));
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }
}
