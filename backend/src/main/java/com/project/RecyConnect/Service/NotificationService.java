package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.NotificationDTO;
import com.project.RecyConnect.Model.Notification;
import com.project.RecyConnect.Repository.NotificationRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class NotificationService {
    private final NotificationRepository repo;
    private final UserRepo userRepo;

    public NotificationService(NotificationRepository repo, UserRepo userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    private NotificationDTO toDTO(Notification n) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(n.getId());
        dto.setCreatedAt(n.getCreatedAt());
        dto.setSenderId(n.getSender() != null ? n.getSender().getId() : null);
        dto.setReceiverId(n.getReceiver() != null ? n.getReceiver().getId() : null);
        dto.setTitle(n.getTitle());
        dto.setMessage(n.getMessage());
        return dto;
    }

    private Notification fromDTO(NotificationDTO dto) {
        Notification n = new Notification();
        n.setId(dto.getId());
        n.setCreatedAt(dto.getCreatedAt());
        n.setTitle(dto.getTitle());
        n.setMessage(dto.getMessage());
        if (dto.getSenderId() != null)
            userRepo.findById(dto.getSenderId()).ifPresent(n::setSender);
        if (dto.getReceiverId() != null)
            userRepo.findById(dto.getReceiverId()).ifPresent(n::setReceiver);
        return n;
    }

    public List<NotificationDTO> findAll() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public Optional<NotificationDTO> findById(Long id) {
        return repo.findById(id).map(this::toDTO);
    }

    public List<NotificationDTO> findByReceiverId(Long receiverId) {
        return repo.findByReceiverId(receiverId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    public NotificationDTO save(NotificationDTO dto) {
        return toDTO(repo.save(fromDTO(dto)));
    }

    public NotificationDTO update(Long id, NotificationDTO dto) {
        return repo.findById(id).map(existing -> {
            existing.setTitle(dto.getTitle());
            existing.setMessage(dto.getMessage());
            return toDTO(repo.save(existing));
        }).orElseThrow(() -> new RuntimeException("Notification not found"));
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }
}
