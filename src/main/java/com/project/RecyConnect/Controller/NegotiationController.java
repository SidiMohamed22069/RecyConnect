package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.EarningsDTO;
import com.project.RecyConnect.DTO.NegotiationDTO;
import com.project.RecyConnect.DTO.NegotiationHistoryDTO;
import com.project.RecyConnect.DTO.TransactionDTO;
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

    /**
     * Les numeros des deux parties d'une offre acceptee.
     *
     * <p>Remplace la lecture de {@code GET /api/users/{id}} que l'application
     * faisait pour recuperer un numero : elle y obtenait la fiche complete de
     * n'importe quel compte, donc l'annuaire (C3 de l'audit). Ici, le serveur
     * ne rend un numero qu'a une partie de la transaction, et seulement une
     * fois l'offre acceptee.
     *
     * <p>Les codes distinguent les refus : 403 si le demandeur est etranger a
     * l'offre, 409 si elle n'est pas encore acceptee. Le 409 n'est pas un
     * detail de style — cote mobile, un 403 sur un appel signe declenche la
     * deconnexion locale, et une offre en attente y suffirait.
     */
    @GetMapping("/{id}/contact")
    public ResponseEntity<?> getContact(@PathVariable Long id) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        NegotiationService.ContactLookup lookup = service.findContact(id, currentUser.getId());
        return switch (lookup.access()) {
            case GRANTED -> ResponseEntity.ok(lookup.contact());
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case NOT_A_PARTY -> ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            case NOT_ACCEPTED -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Contact is available once the offer is accepted"));
        };
    }

    /**
     * La contre-proposition du vendeur.
     *
     * <p>Troisieme reponse possible a une offre, a cote d'accepter et de
     * refuser — et la plus frequente dans une negociation reelle. Le corps
     * porte {@code price} et/ou {@code quantity}.
     */
    @PostMapping("/{id}/counter")
    public ResponseEntity<?> counterOffer(@PathVariable Long id,
                                          @RequestBody NegotiationDTO dto) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return ResponseEntity.ok(service.counterBySeller(
                    id, currentUser.getId(), dto.getPrice(), dto.getQuantity()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Le fil d'une negociation: "25 -> 20 -> 22".
     *
     * <p>Reserve aux deux parties. Les montants successifs d'une negociation
     * sont une information commerciale, au meme titre que l'offre elle-meme.
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<NegotiationHistoryDTO>> history(@PathVariable Long id) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        NegotiationDTO existing = service.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        boolean isParty = currentUser.getId().equals(existing.getSenderId())
                || currentUser.getId().equals(existing.getReceiverId());
        if (!isParty) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(service.historyOf(id));
    }

    /**
     * Le journal des transactions conclues de l'appelant, ventes et achats.
     *
     * <p>Le total etait deja servi par {@code /earnings/me}; il manquait le
     * detail qui en fait un outil de gestion pour un collecteur professionnel.
     */
    @GetMapping("/history/me")
    public ResponseEntity<List<TransactionDTO>> myTransactions() {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(service.transactionsFor(currentUser.getId()));
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
