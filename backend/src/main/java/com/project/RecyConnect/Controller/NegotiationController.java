package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.NegotiationDTO;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Service.NegotiationService;
import com.project.RecyConnect.Service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/negotiations")
public class NegotiationController {

    private final NegotiationService service;
    private final UserService userService;

    public NegotiationController(NegotiationService service, UserService userService) { 
        this.service = service;
        this.userService = userService;
    }

    @GetMapping
    public List<NegotiationDTO> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<NegotiationDTO> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sender/{senderId}")
    public List<NegotiationDTO> getBySender(@PathVariable Long senderId) {
        return service.findBySenderId(senderId);
    }

    @GetMapping("/receiver/{receiverId}")
    public List<NegotiationDTO> getByReceiver(@PathVariable Long receiverId) {
        return service.findByReceiverId(receiverId);
    }

    @GetMapping("/product/{productId}")
    public List<NegotiationDTO> getByProduct(
            @PathVariable Long productId,
            @RequestParam(required = false) String status) {
        return service.findByProductId(productId, status);
    }

    @PostMapping
    public ResponseEntity<NegotiationDTO> create(@RequestBody NegotiationDTO dto) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Définir automatiquement l'utilisateur connecté comme expéditeur de la négociation
        dto.setSenderId(currentUser.getId());
        return ResponseEntity.ok(service.save(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NegotiationDTO> update(@PathVariable Long id, @RequestBody NegotiationDTO dto) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Vérifier que l'utilisateur est soit l'expéditeur soit le destinataire
        NegotiationDTO existing = service.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!existing.getSenderId().equals(currentUser.getId()) && 
            !existing.getReceiverId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            return ResponseEntity.ok(service.update(id, dto));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<NegotiationDTO> patch(@PathVariable Long id, @RequestBody NegotiationDTO dto) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Vérifier que l'utilisateur est soit l'expéditeur soit le destinataire
        NegotiationDTO existing = service.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!existing.getSenderId().equals(currentUser.getId()) && 
            !existing.getReceiverId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            return ResponseEntity.ok(service.patch(id, dto));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Vérifier que l'utilisateur est soit l'expéditeur soit le destinataire
        NegotiationDTO existing = service.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!existing.getSenderId().equals(currentUser.getId()) && 
            !existing.getReceiverId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
