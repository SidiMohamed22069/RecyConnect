package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.EarningsDTO;
import com.project.RecyConnect.DTO.NegotiationDTO;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Service.ModerationService;
import com.project.RecyConnect.Service.NegotiationService;
import com.project.RecyConnect.Service.ProductService;
import com.project.RecyConnect.Service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/negotiations")
public class NegotiationController {

    private final NegotiationService service;
    private final UserService userService;
    private final ProductService productService;
    private final ModerationService moderationService;

    public NegotiationController(NegotiationService service,
                                 UserService userService,
                                 ProductService productService,
                                 ModerationService moderationService) {
        this.service = service;
        this.userService = userService;
        this.productService = productService;
        this.moderationService = moderationService;
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

    /**
     * Les offres en attente sur une annonce.
     *
     * <p>Celles des comptes bloques n'y figurent pas : bloquer quelqu'un doit
     * faire disparaitre ses offres comme ses annonces, sans quoi le blocage ne
     * vaudrait que sur la moitie de l'application.
     */
    @GetMapping("/product/{productId}/queue")
    public List<NegotiationDTO> getQueueByProduct(@PathVariable Long productId) {
        List<NegotiationDTO> queue = service.getQueueByProductId(productId);
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return queue;
        }
        Set<Long> hidden = moderationService.hiddenUserIds(currentUser.getId());
        if (hidden.isEmpty()) {
            return queue;
        }
        return queue.stream()
                .filter(offer -> !hidden.contains(offer.getSenderId()))
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<NegotiationDTO> create(@RequestBody NegotiationDTO dto) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Un blocage coupe l'interaction dans les deux sens : sans ce controle,
        // un compte bloque pouvait continuer a faire des offres — et donc a
        // declencher des notifications — chez celui qui l'avait bloque.
        Long sellerId = productService.findById(dto.getProductId())
                .map(product -> product.getUserId())
                .orElse(null);
        if (sellerId != null && moderationService.isBlockedBetween(currentUser.getId(), sellerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        dto.setSenderId(currentUser.getId());
        try {
            return ResponseEntity.ok(service.save(dto));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<NegotiationDTO> update(@PathVariable Long id, @RequestBody NegotiationDTO dto) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        NegotiationDTO existing = service.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        // Buyer only can modify active offer terms
        if (!existing.getSenderId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            return ResponseEntity.ok(service.update(id, dto));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
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

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOffer(@PathVariable Long id) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return ResponseEntity.ok(service.cancelByBuyer(id, currentUser.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<?> acceptOffer(@PathVariable Long id) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return ResponseEntity.ok(service.acceptBySeller(id, currentUser.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectOffer(@PathVariable Long id) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return ResponseEntity.ok(service.rejectBySeller(id, currentUser.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/earnings/me")
    public ResponseEntity<EarningsDTO> getMyEarnings() {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(service.getSellerEarnings(currentUser.getId()));
    }
}
