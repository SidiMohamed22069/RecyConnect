package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.ProductDTO;
import com.project.RecyConnect.DTO.ProductSearchCriteria;
import com.project.RecyConnect.Model.Moughataa;
import com.project.RecyConnect.Model.ProductStatus;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Service.ModerationService;
import com.project.RecyConnect.Service.ProductService;
import com.project.RecyConnect.Service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;
    private final UserService userService;
    private final ModerationService moderationService;

    public ProductController(ProductService service,
                             UserService userService,
                             ModerationService moderationService) {
        this.service = service;
        this.userService = userService;
        this.moderationService = moderationService;
    }

    @GetMapping
    public List<ProductDTO> getAll() { return service.findAll(); }

    /**
     * La fiche d'une annonce.
     *
     * <p>Reste lisible sans compte — c'est la page qu'on partage. Pour un
     * appelant connecte, elle porte en plus le fait qu'il l'ait enregistree en
     * favori, ce qui evite une seconde requete a l'ouverture.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getById(@PathVariable Long id) {
        User currentUser = userService.getCurrentUser();
        Long currentUserId = currentUser != null ? currentUser.getId() : null;
        return service.findById(id, currentUserId).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * D'autres annonces de la meme categorie.
     *
     * <p>Une fiche produit qui ne menait nulle part renvoyait l'acheteur a
     * l'accueil, sa recherche a recommencer.
     */
    @GetMapping("/{id}/similar")
    public List<ProductDTO> getSimilar(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        User currentUser = userService.getCurrentUser();
        Long currentUserId = currentUser != null ? currentUser.getId() : null;
        return service.findSimilar(id, limit, moderationService.hiddenUserIds(currentUserId));
    }

    /** Les moughataas proposees a la publication et au filtrage. */
    @GetMapping("/locations")
    public List<String> locations() {
        return java.util.Arrays.stream(Moughataa.values())
                .map(Moughataa::getValue)
                .toList();
    }

    @GetMapping("/user/{userId}")
    public List<ProductDTO> getByUser(@PathVariable Long userId) {
        return service.findByUserId(userId);
    }

    @GetMapping("/user/{userId}/status")
    public List<ProductDTO> getByUserWithStatus(
            @PathVariable Long userId,
            @RequestParam(required = false) String status) {
        return service.findByUserIdWithStatus(userId, status);
    }

    @GetMapping("/category/{categoryId}")
    public List<ProductDTO> getByCategory(@PathVariable Long categoryId) {
        return service.findByCategoryId(categoryId);
    }

    /**
     * Recherche d'annonces.
     *
     * <p>Le catalogue reste lisible sans compte : {@code getCurrentUser} rend
     * alors {@code null} et il n'y a personne a masquer. Pour un utilisateur
     * connecte, les annonces des comptes bloques — dans un sens ou dans
     * l'autre — ne partent pas sur le reseau.
     */
    @GetMapping("/search")
    public List<ProductDTO> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long excludeUserId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Long minQuantity,
            @RequestParam(required = false) String unit,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double maxDistanceKm,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        User currentUser = userService.getCurrentUser();
        Long currentUserId = currentUser != null ? currentUser.getId() : null;

        ProductSearchCriteria criteria = ProductSearchCriteria.builder()
                .query(query)
                .categoryId(categoryId)
                .excludeUserId(excludeUserId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .minQuantity(minQuantity)
                .unit(unit)
                // Une zone inconnue ne filtre pas, plutot que de rendre 500:
                // une version installee de l'application peut envoyer un code
                // que le serveur ne connait pas encore, ou l'inverse.
                .location(Moughataa.parseOrNull(location))
                // Un rayon sans centre ne veut rien dire : les trois vont
                // ensemble, et le service ignore le critere s'il en manque un.
                .centerLatitude(lat)
                .centerLongitude(lng)
                .maxDistanceKm(maxDistanceKm)
                .hiddenUserIds(moderationService.hiddenUserIds(currentUserId))
                .sort(sort)
                .page(page)
                .size(size)
                .build();

        return service.search(criteria, currentUserId);
    }

    /**
     * Les annonces visibles dans le rectangle affiche par la carte.
     *
     * <p>Lisible sans compte, comme le catalogue. Les positions rendues sont
     * arrondies a 300 m sauf pour leur auteur : la carte est l'ecran ou la
     * difference se voit.
     *
     * <p>Le rectangle est obligatoire — une carte sans cadre demanderait le
     * catalogue entier, ce que ce point d'entree existe justement pour eviter.
     */
    @GetMapping("/map")
    public List<ProductDTO> map(
            @RequestParam double minLat,
            @RequestParam double maxLat,
            @RequestParam double minLng,
            @RequestParam double maxLng,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long excludeUserId,
            @RequestParam(required = false) Long categoryId) {
        User currentUser = userService.getCurrentUser();
        Long currentUserId = currentUser != null ? currentUser.getId() : null;

        return service.mapArea(minLat, maxLat, minLng, maxLng, limit,
                excludeUserId, categoryId,
                moderationService.hiddenUserIds(currentUserId), currentUserId);
    }

    /**
     * Les annonces les plus proches d'un point, la plus proche d'abord.
     *
     * <p>Sert la section "Pres de vous" de l'accueil. Chaque ligne porte sa
     * distance en kilometres — mais seulement si l'annonce a ses propres
     * coordonnees : une distance calculee depuis le centre d'une moughataa
     * annoncerait une precision que personne n'a mesuree.
     */
    @GetMapping("/nearby")
    public List<ProductDTO> nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long excludeUserId) {
        User currentUser = userService.getCurrentUser();
        Long currentUserId = currentUser != null ? currentUser.getId() : null;

        return service.nearby(lat, lng, radiusKm, limit, excludeUserId,
                moderationService.hiddenUserIds(currentUserId), currentUserId);
    }

    @PostMapping
    public ResponseEntity<ProductDTO> create(@RequestBody ProductDTO dto) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Le proprietaire est l'auteur de l'appel — sauf pour un administrateur,
        // qui publie depuis le panneau au nom d'un vendeur reel. Ecraser userId
        // sans distinction rattachait au compte admin toutes les annonces
        // saisies pour autrui.
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        if (!isAdmin || dto.getUserId() == null) {
            dto.setUserId(currentUser.getId());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody ProductDTO dto) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Vérifier que l'utilisateur est propriétaire du produit
        ProductDTO existing = service.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        // VÉRIFICATION: Seul ADMIN ou propriétaire peut modifier
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isOwner = existing.getUserId().equals(currentUser.getId());
        
        if (!isAdmin && !isOwner) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "You can only update your own products"));
        }
        try {
            return ResponseEntity.ok(service.update(id, dto, currentUser.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> patch(@PathVariable Long id, @RequestBody ProductDTO dto) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Vérifier que l'utilisateur est propriétaire du produit
        ProductDTO existing = service.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        // VÉRIFICATION: Seul ADMIN ou propriétaire peut modifier
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isOwner = existing.getUserId().equals(currentUser.getId());
        
        if (!isAdmin && !isOwner) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "You can only update your own products"));
        }
        try {
            return ResponseEntity.ok(service.patch(id, dto, currentUser.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Change le statut d'une annonce: disponible, en pause, vendue.
     *
     * <p>Un vendeur pouvait tout modifier sauf cela: son annonce restait
     * visible et continuait de recevoir des offres qu'il ne pouvait honorer,
     * et la seule sortie etait la suppression — qui lui faisait perdre son
     * anciennete et ses offres.
     *
     * <p>Seuls les trois statuts choisis par un vendeur sont acceptes ici:
     * "en attente" est un etat du serveur et "archive" le resultat d'une
     * suppression, ni l'un ni l'autre ne se demandent.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ProductDTO existing = service.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isOwner = existing.getUserId() != null
                && existing.getUserId().equals(currentUser.getId());
        if (!isAdmin && !isOwner) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You can only update your own products"));
        }

        ProductStatus target;
        try {
            target = ProductStatus.fromValue(body.get("status"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
        if (target == null || !ProductStatus.SELECTABLE_BY_OWNER.contains(target)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Status must be one of: available, paused, recycled"));
        }

        ProductDTO patch = new ProductDTO();
        patch.setStatus(target);
        return ResponseEntity.ok(service.patch(id, patch));
    }

    @PostMapping("/{id}/accept-offer")
    public ResponseEntity<ProductDTO> acceptOffer(
            @PathVariable Long id,
            @RequestBody Map<String, Long> request) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Vérifier que l'utilisateur est propriétaire du produit
        ProductDTO existing = service.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!existing.getUserId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            Long quantityOffer = request.get("quantityOffer");
            return ResponseEntity.ok(service.updateQuantity(id, quantityOffer));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Vérifier que l'utilisateur est propriétaire du produit
        ProductDTO existing = service.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        // VÉRIFICATION: Seul ADMIN ou propriétaire peut supprimer
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isOwner = existing.getUserId().equals(currentUser.getId());
        
        if (!isAdmin && !isOwner) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "You can only delete your own products"));
        }
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Endpoint admin pour modifier n'importe quel produit
     */
    @PutMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminUpdateProduct(@PathVariable Long id, @RequestBody ProductDTO dto) {
        ProductDTO existing = service.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            // Admin peut tout modifier, y compris changer le propriétaire.
            // Le point GPS exact ne lui est pas rendu pour autant : corriger
            // une annonce ne demande pas de connaître l'adresse d'un
            // particulier.
            User currentUser = userService.getCurrentUser();
            return ResponseEntity.ok(service.update(id, dto,
                    currentUser != null ? currentUser.getId() : null));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
