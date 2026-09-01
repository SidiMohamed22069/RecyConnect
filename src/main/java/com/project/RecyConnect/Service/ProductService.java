package com.project.RecyConnect.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.RecyConnect.DTO.ProductDTO;
import com.project.RecyConnect.DTO.ProductSearchCriteria;
import com.project.RecyConnect.Model.NegotiationStatus;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.ProductStatus;
import com.project.RecyConnect.Repository.CategoryRepository;
import com.project.RecyConnect.Repository.FavoriteRepository;
import com.project.RecyConnect.Repository.NegotiationRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.UserRepo;

@Service
public class ProductService {

    private final ProductRepository repo;
    private final CategoryRepository categoryRepo;
    private final UserRepo userRepo;
    private final NegotiationService negotiationService;
    private final FileUrlService fileUrlService;
    private final NegotiationRepository negotiationRepo;
    private final FavoriteRepository favoriteRepo;
    private final SearchAlertService searchAlertService;

    public ProductService(ProductRepository repo, CategoryRepository categoryRepo,
                          UserRepo userRepo, NegotiationService negotiationService,
                          FileUrlService fileUrlService,
                          NegotiationRepository negotiationRepo,
                          FavoriteRepository favoriteRepo,
                          SearchAlertService searchAlertService) {
        this.repo = repo;
        this.categoryRepo = categoryRepo;
        this.userRepo = userRepo;
        this.negotiationService = negotiationService;
        this.fileUrlService = fileUrlService;
        this.negotiationRepo = negotiationRepo;
        this.favoriteRepo = favoriteRepo;
        this.searchAlertService = searchAlertService;
    }

    private ProductDTO toDTO(Product p) {
        ProductDTO dto = new ProductDTO();
        dto.setId(p.getId());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setTitle(p.getTitle());
        dto.setDesc(p.getDescription());
        dto.setPrice(p.getPrice());
        dto.setUnit(p.getUnit());
        dto.setQuantityTotal(p.getQuantityTotal());
        dto.setQuantityAvailable(p.getQuantityAvailable());
        dto.setStatus(p.getStatus());
        dto.setLocation(p.getLocation());
        // Les URL sont reecrites vers l'hote courant: les annonces creees quand
        // le serveur avait une autre adresse restent affichables.
        dto.setImageUrls(fileUrlService.toPublicUrls(p.getImageUrls()));
        dto.setCategoryId(p.getCategory() != null ? p.getCategory().getId() : null);
        dto.setUserId(p.getUser() != null ? p.getUser().getId() : null);
        // Add nested info
        dto.setCategoryName(p.getCategory() != null ? p.getCategory().getName() : null);
        dto.setUserName(p.getUser() != null ? p.getUser().getUsername() : null);
        dto.setUserPhone(p.getUser() != null ? p.getUser().getPhone() : null);
        return dto;
    }

    /**
     * La meme traduction que {@link #toDTO(Product)}, ouverte aux services qui
     * detiennent deja l'entite — les favoris, notamment — et n'ont aucune
     * raison de la relire par son identifiant.
     */
    public ProductDTO toPublicDTO(Product product) {
        return toDTO(product);
    }

    private Product fromDTO(ProductDTO dto) {
        Product p = Product.builder()
                .id(dto.getId())
                .createdAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : OffsetDateTime.now())
                .title(dto.getTitle())
                .description(dto.getDesc())
                .price(dto.getPrice())
                .unit(dto.getUnit())
                .quantityTotal(dto.getQuantityTotal())
                .quantityAvailable(dto.getQuantityAvailable())
                .status(dto.getStatus())
                .location(dto.getLocation())
                .imageUrls(dto.getImageUrls())
                .build();
        if (dto.getCategoryId() != null)
            categoryRepo.findById(dto.getCategoryId()).ifPresent(p::setCategory);
        if (dto.getUserId() != null)
            userRepo.findById(dto.getUserId()).ifPresent(p::setUser);
        return p;
    }

    @Transactional(readOnly = true)
    public List<ProductDTO> findAll() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ProductDTO> findById(Long id) {
        return repo.findById(id).map(this::toDTO);
    }

    /**
     * La fiche d'une annonce, enrichie de ce qui ne vaut que pour elle: le
     * nombre d'offres en cours et le fait que l'appelant l'ait enregistree.
     *
     * <p>Le compte des offres n'est pas rendu dans les listes: il couterait
     * une requete par ligne, pour une information qu'on ne lit que sur la
     * fiche.
     */
    @Transactional(readOnly = true)
    public Optional<ProductDTO> findById(Long id, Long currentUserId) {
        return repo.findById(id).map(product -> {
            ProductDTO dto = toDTO(product);
            dto.setPendingOffersCount(negotiationRepo.countByProductIdAndStatusIgnoreCase(
                    id, NegotiationStatus.STATUS_PENDING));
            if (currentUserId != null) {
                dto.setFavorite(favoriteRepo.existsByUserIdAndProductId(currentUserId, id));
            }
            return dto;
        });
    }

    /**
     * D'autres annonces de la meme categorie, hors celle qu'on regarde.
     *
     * <p>Une fiche produit qui se termine par un cul-de-sac renvoie l'acheteur
     * a l'accueil; quelques lots comparables lui evitent de recommencer sa
     * recherche.
     */
    @Transactional(readOnly = true)
    public List<ProductDTO> findSimilar(Long productId, int limit, Set<Long> hiddenUserIds) {
        Product reference = repo.findById(productId).orElse(null);
        if (reference == null || reference.getCategory() == null) {
            return List.of();
        }
        Set<Long> hidden = hiddenUserIds == null ? Set.of() : hiddenUserIds;
        return repo.findByCategoryId(reference.getCategory().getId()).stream()
                .filter(p -> !p.getId().equals(productId))
                .filter(p -> p.getStatus() == ProductStatus.AVAILABLE)
                .filter(p -> p.getUser() == null || !hidden.contains(p.getUser().getId()))
                .sorted(comparatorFor(null))
                .limit(Math.max(1, limit))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductDTO> findByUserId(Long userId) {
        return repo.findByUserId(userId).stream()
                .filter(p -> p.getStatus() != ProductStatus.ARCHIVED)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductDTO> findByCategoryId(Long categoryId) {
        return repo.findByCategoryId(categoryId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductDTO> search(String query, Long categoryId, Long excludeUserId) {
        return search(query, categoryId, excludeUserId, Set.of());
    }

    /**
     * Recherche d'annonces, en masquant celles des comptes de
     * {@code hiddenUserIds}.
     *
     * <p>Ce sont les comptes bloques par l'appelant, et ceux qui l'ont bloque :
     * le blocage est declare dans un sens et s'applique dans les deux. Le
     * filtrage cote client suffisait a masquer, mais la liste continuait de
     * partir sur le reseau ; ici, elle ne quitte plus le serveur.
     */
    @Transactional(readOnly = true)
    public List<ProductDTO> search(String query, Long categoryId, Long excludeUserId,
                                   Set<Long> hiddenUserIds) {
        return search(ProductSearchCriteria.builder()
                .query(query)
                .categoryId(categoryId)
                .excludeUserId(excludeUserId)
                .hiddenUserIds(hiddenUserIds)
                .build(), null);
    }

    /**
     * Recherche d'annonces filtree, triee et paginee.
     *
     * <p>L'ordre est desormais explicite. Les resultats etaient melanges au
     * hasard a chaque chargement, cote client: l'annonce vue la veille etait
     * introuvable le lendemain, la pagination n'avait aucun sens — deux pages
     * tirees de deux melanges differents se recouvrent — et les favoris
     * n'avaient rien de stable a designer. Le tri par defaut est la date de
     * publication decroissante.
     *
     * <p>La pagination reste facultative: sans {@code size}, le catalogue part
     * entier, comme avant. C'est ce qui permet a une version installee de
     * l'application de continuer a fonctionner sans rien savoir des pages.
     *
     * @param currentUserId l'appelant, pour marquer ses favoris; {@code null}
     *                      pour une lecture anonyme.
     */
    @Transactional(readOnly = true)
    public List<ProductDTO> search(ProductSearchCriteria criteria, Long currentUserId) {
        Set<Long> hidden = criteria.getHiddenUserIds() == null ? Set.of() : criteria.getHiddenUserIds();
        String needle = criteria.getQuery() == null ? null : criteria.getQuery().trim().toLowerCase();
        Long excludeUserId = criteria.getExcludeUserId();
        Long categoryId = criteria.getCategoryId();

        List<Product> matches = repo.findAll().stream()
                .filter(p -> p.getStatus() == ProductStatus.AVAILABLE)
                .filter(p -> excludeUserId == null || p.getUser() == null || !p.getUser().getId().equals(excludeUserId))
                .filter(p -> p.getUser() == null || !hidden.contains(p.getUser().getId()))
                .filter(p -> categoryId == null || (p.getCategory() != null && p.getCategory().getId().equals(categoryId)))
                .filter(p -> needle == null || needle.isEmpty() || matchesText(p, needle))
                .filter(p -> criteria.getMinPrice() == null
                        || (p.getPrice() != null && p.getPrice() >= criteria.getMinPrice()))
                .filter(p -> criteria.getMaxPrice() == null
                        || (p.getPrice() != null && p.getPrice() <= criteria.getMaxPrice()))
                .filter(p -> criteria.getMinQuantity() == null
                        || (p.getQuantityAvailable() != null && p.getQuantityAvailable() >= criteria.getMinQuantity()))
                .filter(p -> criteria.getUnit() == null || criteria.getUnit().isBlank()
                        || criteria.getUnit().equalsIgnoreCase(p.getUnit()))
                .filter(p -> criteria.getLocation() == null || criteria.getLocation() == p.getLocation())
                .sorted(comparatorFor(criteria.getSort()))
                .collect(Collectors.toList());

        List<Product> page = paginate(matches, criteria.getPage(), criteria.getSize());
        List<ProductDTO> rows = page.stream().map(this::toDTO).collect(Collectors.toList());
        markFavorites(rows, currentUserId);
        return rows;
    }

    /**
     * Le texte cherche apparait-il dans l'annonce ?
     *
     * <p>La description compte autant que le titre: une recherche "cuivre"
     * doit rendre le lot intitule "Lot de cables" dont la description precise
     * la matiere.
     */
    private boolean matchesText(Product p, String needle) {
        if (p.getTitle() != null && p.getTitle().toLowerCase().contains(needle)) {
            return true;
        }
        return p.getDescription() != null && p.getDescription().toLowerCase().contains(needle);
    }

    /**
     * L'ordre demande, ou la date de publication decroissante a defaut.
     *
     * <p>Chaque comparateur se termine par l'identifiant: sans ce dernier
     * critere, deux annonces de meme prix pourraient s'echanger d'une page a
     * l'autre et l'une des deux ne serait jamais vue.
     */
    private Comparator<Product> comparatorFor(String sort) {
        Comparator<Product> byId = Comparator.comparing(Product::getId,
                Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<Product> byNewest = Comparator.comparing(Product::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder()));

        if (sort == null || sort.isBlank()) {
            return byNewest.thenComparing(byId.reversed());
        }
        return switch (sort.toLowerCase()) {
            case "price_asc" -> Comparator.comparing(Product::getPrice,
                    Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(byId);
            case "price_desc" -> Comparator.comparing(Product::getPrice,
                    Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(byId);
            case "quantity_desc" -> Comparator.comparing(Product::getQuantityAvailable,
                    Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(byId);
            case "oldest" -> Comparator.comparing(Product::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(byId);
            default -> byNewest.thenComparing(byId.reversed());
        };
    }

    /**
     * La tranche demandee, ou la liste entiere si aucune taille n'est donnee.
     *
     * <p>Une page au-dela de la fin rend une liste vide plutot qu'une erreur:
     * c'est ainsi qu'un defilement infini apprend qu'il a tout vu.
     */
    private <T> List<T> paginate(List<T> rows, Integer page, Integer size) {
        if (size == null || size <= 0) {
            return rows;
        }
        int from = Math.max(0, (page == null ? 0 : page)) * size;
        if (from >= rows.size()) {
            return List.of();
        }
        return rows.subList(from, Math.min(rows.size(), from + size));
    }

    /** Marque d'un coup les annonces que l'appelant a enregistrees. */
    private void markFavorites(List<ProductDTO> rows, Long currentUserId) {
        if (currentUserId == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream().map(ProductDTO::getId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        // Une seule requete pour toute la page: interroger le depot annonce par
        // annonce rendait N requetes pour afficher N cartes.
        Set<Long> favorites = favoriteRepo.findByUserIdAndProductIdIn(currentUserId, ids).stream()
                .map(f -> f.getProduct().getId())
                .collect(Collectors.toSet());
        rows.forEach(row -> row.setFavorite(favorites.contains(row.getId())));
    }

    @Transactional(readOnly = true)
    public List<ProductDTO> findByUserIdWithStatus(Long userId, String status) {
        ProductStatus filterStatus = parseStatus(status);
        return repo.findByUserId(userId).stream()
                .filter(p -> filterStatus == null || filterStatus == p.getStatus())
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private ProductStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return ProductStatus.fromValue(status);
    }

    @Transactional
    public ProductDTO save(ProductDTO dto) {
        Product saved = repo.save(fromDTO(dto));
        // Les veilles se declenchent a la publication, une fois l'annonce
        // ecrite: une alerte qui partirait avant l'enregistrement designerait
        // une annonce que le destinataire ne pourrait pas ouvrir.
        searchAlertService.notifyMatching(saved);
        return toDTO(saved);
    }

    @Transactional
    public ProductDTO update(Long id, ProductDTO dto) {
        return repo.findById(id).map(existing -> {
            existing.setTitle(dto.getTitle());
            existing.setDescription(dto.getDesc());
            existing.setPrice(dto.getPrice());
            existing.setUnit(dto.getUnit());
            existing.setQuantityTotal(dto.getQuantityTotal());
            existing.setQuantityAvailable(dto.getQuantityAvailable());
            existing.setStatus(dto.getStatus());
            existing.setLocation(dto.getLocation());
            existing.setImageUrls(dto.getImageUrls());
            Product saved = repo.save(existing);
            negotiationService.onProductStockChanged(saved.getId(),
                    saved.getUser() != null ? saved.getUser().getId() : null);
            return toDTO(saved);
        }).orElseThrow(() -> new RuntimeException("Product not found"));
    }

    @Transactional
    public ProductDTO patch(Long id, ProductDTO dto) {
        return repo.findById(id).map(existing -> {
            if (dto.getTitle() != null) existing.setTitle(dto.getTitle());
            if (dto.getDesc() != null) existing.setDescription(dto.getDesc());
            if (dto.getPrice() != null) existing.setPrice(dto.getPrice());
            if (dto.getUnit() != null) existing.setUnit(dto.getUnit());
            if (dto.getQuantityTotal() != null) existing.setQuantityTotal(dto.getQuantityTotal());
            if (dto.getQuantityAvailable() != null) existing.setQuantityAvailable(dto.getQuantityAvailable());
            if (dto.getStatus() != null) existing.setStatus(dto.getStatus());
            if (dto.getLocation() != null) existing.setLocation(dto.getLocation());
            if (dto.getImageUrls() != null) existing.setImageUrls(dto.getImageUrls());
            if (dto.getCategoryId() != null)
                categoryRepo.findById(dto.getCategoryId()).ifPresent(existing::setCategory);
            if (dto.getUserId() != null)
                userRepo.findById(dto.getUserId()).ifPresent(existing::setUser);
            Product saved = repo.save(existing);
            negotiationService.onProductStockChanged(saved.getId(),
                    saved.getUser() != null ? saved.getUser().getId() : null);
            return toDTO(saved);
        }).orElseThrow(() -> new RuntimeException("Product not found"));
    }

    @Transactional
    public ProductDTO updateQuantity(Long productId, Long quantityOffer) {
        return repo.findById(productId).map(existing -> {
            Long newQuantity = existing.getQuantityAvailable() - quantityOffer;
            existing.setQuantityAvailable(newQuantity);
            if (newQuantity <= 0) {
                existing.setStatus(ProductStatus.RECYCLED);
            }
            Product saved = repo.save(existing);
            negotiationService.onProductStockChanged(saved.getId(),
                    saved.getUser() != null ? saved.getUser().getId() : null);
            return toDTO(saved);
        }).orElseThrow(() -> new RuntimeException("Product not found"));
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }
}
