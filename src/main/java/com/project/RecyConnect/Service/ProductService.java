package com.project.RecyConnect.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.RecyConnect.DTO.ProductDTO;
import com.project.RecyConnect.DTO.ProductSearchCriteria;
import com.project.RecyConnect.Model.GeoPrecision;
import com.project.RecyConnect.Model.Moughataa;
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
        return toDTO(p, null, false);
    }

    /**
     * L'annonce telle que {@code viewerId} a le droit de la voir.
     *
     * <p>Le seul champ dont la valeur depende de l'appelant est la position :
     * tout le reste est public. Voir {@link #applyGeoVisibility}.
     *
     * @param allowExact vrai quand l'appelant a une offre acceptee sur cette
     *                   annonce — calcule uniquement sur la fiche, ou une
     *                   requete de plus est acceptable.
     */
    private ProductDTO toDTO(Product p, Long viewerId, boolean allowExact) {
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
        applyGeoVisibility(dto, p, viewerId, allowExact);
        return dto;
    }

    /**
     * Ecrit la position dans la reponse — arrondie, sauf pour ceux a qui elle
     * est due.
     *
     * <p>C'est ici, et nulle part ailleurs, que la promesse faite au vendeur
     * est tenue. Une application peut bien arrondir ce qu'elle dessine : si le
     * serveur a envoye le point exact, la valeur precise a deja quitte la
     * maison, et il suffit de lire la reponse HTTP pour retrouver l'adresse.
     *
     * <p>Trois personnes voient le point exact d'une annonce declaree
     * approximative : son auteur, un administrateur — non, pas meme lui, il n'a
     * aucune raison de connaitre l'adresse d'un particulier — et l'acheteur
     * dont l'offre a ete acceptee, au moment ou les deux ont deja echange leurs
     * numeros. Tous les autres recoivent le centre d'une case de 300 m.
     */
    private void applyGeoVisibility(ProductDTO dto, Product p, Long viewerId, boolean allowExact) {
        if (!GeoSupport.isValid(p.getLatitude(), p.getLongitude())) {
            return;
        }

        GeoPrecision precision =
                p.getGeoPrecision() == null ? GeoPrecision.APPROX : p.getGeoPrecision();
        dto.setGeoPrecision(precision);

        boolean isOwner = viewerId != null
                && p.getUser() != null
                && viewerId.equals(p.getUser().getId());

        if (precision == GeoPrecision.EXACT || isOwner || allowExact) {
            dto.setLatitude(p.getLatitude());
            dto.setLongitude(p.getLongitude());
            return;
        }

        double[] blurred = GeoSupport.blur(p.getLatitude(), p.getLongitude());
        dto.setLatitude(blurred[0]);
        dto.setLongitude(blurred[1]);
    }

    /**
     * Le point auquel une annonce se situe, du plus precis au plus vague : le
     * sien, a defaut le centre de sa moughataa, a defaut rien.
     *
     * <p>Sert a filtrer et a classer, jamais a afficher : c'est pourquoi le
     * repli sur le quartier y est admis. Une distance <em>montree</em> a
     * l'utilisateur, elle, ne se calcule que sur un vrai point.
     */
    private double[] effectivePoint(Product p) {
        if (GeoSupport.isValid(p.getLatitude(), p.getLongitude())) {
            return new double[] { p.getLatitude(), p.getLongitude() };
        }
        Moughataa zone = p.getLocation();
        if (zone != null && zone.hasCentroid()) {
            return new double[] { zone.getCentroidLatitude(), zone.getCentroidLongitude() };
        }
        return null;
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
        applyGeoFrom(dto, p);
        if (dto.getCategoryId() != null)
            categoryRepo.findById(dto.getCategoryId()).ifPresent(p::setCategory);
        if (dto.getUserId() != null)
            userRepo.findById(dto.getUserId()).ifPresent(p::setUser);
        return p;
    }

    /**
     * Reporte la position recue sur l'annonce, si elle en est une.
     *
     * <p>Les trois champs voyagent ensemble : une latitude sans longitude ne
     * designe rien, et un couple hors bornes — ou (0, 0), qui est presque
     * toujours un champ vide plutot qu'un point dans le golfe de Guinee — est
     * ignore plutot que d'etre stocke puis dessine au large de l'Afrique.
     */
    private void applyGeoFrom(ProductDTO dto, Product p) {
        if (!GeoSupport.isValid(dto.getLatitude(), dto.getLongitude())) {
            p.setLatitude(null);
            p.setLongitude(null);
            p.setGeoPrecision(null);
            return;
        }
        p.setLatitude(dto.getLatitude());
        p.setLongitude(dto.getLongitude());
        // Sans precision declaree, la plus prudente : une version de
        // l'application qui n'enverrait pas le champ ne doit pas publier des
        // adresses que personne n'a accepte de rendre publiques.
        p.setGeoPrecision(dto.getGeoPrecision() == null
                ? GeoPrecision.APPROX
                : dto.getGeoPrecision());
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
            // La fiche est le seul endroit ou l'on interroge les offres de
            // l'appelant : une requete de plus y est acceptable, alors qu'en
            // liste elle en couterait une par ligne.
            boolean allowExact = currentUserId != null
                    && negotiationRepo.existsBySenderIdAndProductIdAndStatusIgnoreCase(
                            currentUserId, id, NegotiationStatus.STATUS_ACCEPTED);

            ProductDTO dto = toDTO(product, currentUserId, allowExact);
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
                .sorted(comparatorFor(ProductSearchCriteria.builder().build(), null))
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
                .filter(p -> withinRadius(p, criteria))
                .sorted(comparatorFor(criteria, center(criteria)))
                .collect(Collectors.toList());

        List<Product> page = paginate(matches, criteria.getPage(), criteria.getSize());
        List<ProductDTO> rows = page.stream()
                .map(p -> withDistance(toDTO(p, currentUserId, false), p, center(criteria)))
                .collect(Collectors.toList());
        markFavorites(rows, currentUserId);
        return rows;
    }

    /** Le centre d'une recherche par rayon, ou {@code null}. */
    private double[] center(ProductSearchCriteria criteria) {
        if (!GeoSupport.isValid(criteria.getCenterLatitude(), criteria.getCenterLongitude())) {
            return null;
        }
        return new double[] { criteria.getCenterLatitude(), criteria.getCenterLongitude() };
    }

    /**
     * L'annonce tombe-t-elle dans le rayon demande ?
     *
     * <p>Sans centre ou sans rayon, le critere ne filtre pas — un rayon sans
     * centre ne veut rien dire, et vider le catalogue serait la pire des
     * reponses. Une annonce qu'on ne sait pas situer, en revanche, sort d'une
     * recherche par rayon : la faire passer pour proche serait mentir.
     */
    private boolean withinRadius(Product p, ProductSearchCriteria criteria) {
        double[] from = center(criteria);
        Double radius = criteria.getMaxDistanceKm();
        if (from == null || radius == null || radius <= 0) {
            return true;
        }
        double[] point = effectivePoint(p);
        if (point == null) {
            return false;
        }
        return GeoSupport.distanceKm(from[0], from[1], point[0], point[1]) <= radius;
    }

    /**
     * Ajoute la distance a une annonce, quand elle a un sens.
     *
     * <p>Seules les annonces qui portent leurs propres coordonnees en
     * recoivent une : annoncer "2,3 km" a partir du centre d'une moughataa
     * donnerait une precision que personne n'a mesuree. Le classement, lui,
     * peut se contenter du quartier — il ordonne, il n'affirme rien.
     */
    private ProductDTO withDistance(ProductDTO dto, Product p, double[] from) {
        if (from == null || !GeoSupport.isValid(p.getLatitude(), p.getLongitude())) {
            return dto;
        }
        dto.setDistanceKm(GeoSupport.distanceKm(
                from[0], from[1], p.getLatitude(), p.getLongitude()));
        return dto;
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
    private Comparator<Product> comparatorFor(ProductSearchCriteria criteria, double[] from) {
        String sort = criteria.getSort();
        Comparator<Product> byId = Comparator.comparing(Product::getId,
                Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<Product> byNewest = Comparator.comparing(Product::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder()));

        if (sort == null || sort.isBlank()) {
            return byNewest.thenComparing(byId.reversed());
        }
        // "distance" suppose un centre. Sans lui, le tri retombe sur la
        // fraicheur plutot que de rendre un ordre arbitraire presente comme un
        // classement par proximite.
        if ("distance".equalsIgnoreCase(sort) && from != null) {
            return Comparator.comparingDouble((Product p) -> distanceOrFar(p, from))
                    .thenComparing(byId);
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
     * La distance d'une annonce au centre, ou l'infini si on ne sait pas la
     * situer — ce qui la renvoie en fin de classement plutot que de la placer
     * au hasard.
     */
    private double distanceOrFar(Product p, double[] from) {
        double[] point = effectivePoint(p);
        if (point == null) {
            return Double.MAX_VALUE;
        }
        return GeoSupport.distanceKm(from[0], from[1], point[0], point[1]);
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
        Product product = fromDTO(dto);
        product.setStatus(publishableStatus(dto.getStatus()));
        Product saved = repo.save(product);
        // Les veilles se declenchent a la publication, une fois l'annonce
        // ecrite: une alerte qui partirait avant l'enregistrement designerait
        // une annonce que le destinataire ne pourrait pas ouvrir.
        searchAlertService.notifyMatching(saved);
        return toDTO(saved, saved.getUser() != null ? saved.getUser().getId() : null, true);
    }

    /**
     * Le statut d'une annonce qu'on vient de publier.
     *
     * <p>Corrige une annonce invisible. L'application mobile envoyait
     * {@code "pending"} a la creation ; la recherche ne rend que les annonces
     * {@code AVAILABLE} ; et <b>rien, nulle part, ne fait passer une annonce de
     * {@code PENDING} a {@code AVAILABLE}</b> — il n'existe aucune file de
     * moderation, {@code PENDING} n'etant utilise que par le jeu de
     * demonstration. Toute annonce publiee depuis l'application etait donc
     * enregistree, facturee en donnees mobiles… et introuvable, pour son auteur
     * comme pour tout le monde.
     *
     * <p>Le statut d'une annonce nouvelle n'appartient de toute facon pas au
     * client : un statut qu'un vendeur ne peut pas choisir lui-meme
     * ({@code PENDING}, {@code ARCHIVED}) n'a pas a pouvoir etre demande par un
     * appel HTTP. Le serveur tranche, et les versions de l'application deja
     * installees se remettent a fonctionner sans mise a jour.
     *
     * <p>Les annonces deja bloquees, elles, ne se debloquent pas seules : leur
     * auteur peut les rendre disponibles depuis "mes annonces", ou un
     * administrateur d'un coup avec
     * {@code UPDATE product SET status = 'AVAILABLE' WHERE status = 'PENDING';}
     */
    private ProductStatus publishableStatus(ProductStatus requested) {
        if (requested != null && ProductStatus.SELECTABLE_BY_OWNER.contains(requested)) {
            return requested;
        }
        return ProductStatus.AVAILABLE;
    }

    @Transactional
    public ProductDTO update(Long id, ProductDTO dto) {
        return update(id, dto, null);
    }

    /**
     * @param callerId l'auteur de la requete, pour decider ce que la reponse
     *                 montre de la position. Un administrateur qui corrige
     *                 l'annonce d'autrui n'a pas plus besoin de son adresse
     *                 qu'un acheteur.
     */
    @Transactional
    public ProductDTO update(Long id, ProductDTO dto, Long callerId) {
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
            // PUT remplace l'annonce entiere : une position absente du corps
            // efface celle qui existait, comme pour les autres champs.
            applyGeoFrom(dto, existing);
            Product saved = repo.save(existing);
            negotiationService.onProductStockChanged(saved.getId(),
                    saved.getUser() != null ? saved.getUser().getId() : null);
            return toDTO(saved, callerId, false);
        }).orElseThrow(() -> new RuntimeException("Product not found"));
    }

    @Transactional
    public ProductDTO patch(Long id, ProductDTO dto) {
        return patch(id, dto, null);
    }

    /** Voir {@link #update(Long, ProductDTO, Long)} pour {@code callerId}. */
    @Transactional
    public ProductDTO patch(Long id, ProductDTO dto, Long callerId) {
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
            // PATCH ne touche que ce qu'on lui donne : une position transmise
            // remplace la precedente, une position absente laisse celle qui
            // existe. Retirer un point demande donc un PUT — le cas est rare, et
            // un corps partiel ne sait pas distinguer "absent" de "a effacer".
            if (GeoSupport.isValid(dto.getLatitude(), dto.getLongitude())) {
                applyGeoFrom(dto, existing);
            } else if (dto.getGeoPrecision() != null) {
                // Changer d'avis sur ce qu'on montre, sans redonner le point.
                existing.setGeoPrecision(dto.getGeoPrecision());
            }
            Product saved = repo.save(existing);
            negotiationService.onProductStockChanged(saved.getId(),
                    saved.getUser() != null ? saved.getUser().getId() : null);
            return toDTO(saved, callerId, false);
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

    // ------------------------------------------------------------- la carte

    /** Ce qu'une lecture de carte rend par defaut, et au plus. */
    private static final int MAP_DEFAULT_LIMIT = 200;
    private static final int MAP_MAX_LIMIT = 500;

    /** Rayon par defaut d'une recherche de proximite, et son maximum. */
    private static final double NEARBY_DEFAULT_RADIUS_KM = 10.0;
    private static final double NEARBY_MAX_RADIUS_KM = 100.0;

    /**
     * Les annonces visibles dans le rectangle affiche par la carte.
     *
     * <p>Une carte qui redemanderait le catalogue entier a chaque deplacement
     * serait inutilisable — et impayable sur un forfait mobile. La lecture part
     * donc d'une requete bornee au rectangle ({@code findInBounds}), a laquelle
     * s'ajoutent les annonces sans coordonnees dont la moughataa a son centre
     * dans le cadre : sans elles, la carte serait vide, aucune annonce publiee
     * avant son arrivee ne portant de point.
     *
     * <p>Les positions rendues suivent la meme regle que partout ailleurs :
     * arrondies a 300 m, sauf pour leur auteur. Une carte est justement l'ecran
     * ou la difference se voit.
     */
    @Transactional(readOnly = true)
    public List<ProductDTO> mapArea(double minLat, double maxLat,
                                    double minLng, double maxLng,
                                    Integer limit, Long excludeUserId, Long categoryId,
                                    Set<Long> hiddenUserIds, Long currentUserId) {
        // Un rectangle donne a l'envers est un rectangle : le remettre a
        // l'endroit coute moins cher qu'un 400 que personne ne saura lire.
        double south = Math.min(minLat, maxLat);
        double north = Math.max(minLat, maxLat);
        double west = Math.min(minLng, maxLng);
        double east = Math.max(minLng, maxLng);

        List<Moughataa> zones = zonesWithin(south, north, west, east);

        List<Product> candidates = new ArrayList<>(
                repo.findInBounds(ProductStatus.AVAILABLE, south, north, west, east));
        if (!zones.isEmpty()) {
            candidates.addAll(repo.findWithoutPointInZones(ProductStatus.AVAILABLE, zones));
        }

        Set<Long> hidden = hiddenUserIds == null ? Set.of() : hiddenUserIds;
        int cap = boundedLimit(limit);

        return dedupe(candidates).stream()
                .filter(p -> excludeUserId == null || p.getUser() == null
                        || !p.getUser().getId().equals(excludeUserId))
                .filter(p -> p.getUser() == null || !hidden.contains(p.getUser().getId()))
                .filter(p -> categoryId == null
                        || (p.getCategory() != null && p.getCategory().getId().equals(categoryId)))
                // Les plus recentes d'abord : si la zone en contient plus que
                // la limite, autant montrer celles qui viennent d'arriver.
                .sorted(comparatorFor(ProductSearchCriteria.builder().build(), null))
                .limit(cap)
                .map(p -> toDTO(p, currentUserId, false))
                .collect(Collectors.toList());
    }

    /**
     * Les annonces les plus proches d'un point.
     *
     * <p>Sert la section "Pres de vous" de l'accueil. Le rectangle deduit du
     * rayon sert de premier filtre — c'est lui qui laisse la base se servir
     * d'un index — puis la distance exacte tranche et ordonne.
     */
    @Transactional(readOnly = true)
    public List<ProductDTO> nearby(double latitude, double longitude,
                                   Double radiusKm, Integer limit,
                                   Long excludeUserId, Set<Long> hiddenUserIds,
                                   Long currentUserId) {
        double radius = radiusKm == null || radiusKm <= 0
                ? NEARBY_DEFAULT_RADIUS_KM
                : Math.min(radiusKm, NEARBY_MAX_RADIUS_KM);

        double latSpan = GeoSupport.latitudeDegreesFor(radius);
        double lngSpan = GeoSupport.longitudeDegreesFor(radius, latitude);

        List<Moughataa> zones = zonesWithin(
                latitude - latSpan, latitude + latSpan,
                longitude - lngSpan, longitude + lngSpan);

        List<Product> candidates = new ArrayList<>(repo.findInBounds(
                ProductStatus.AVAILABLE,
                latitude - latSpan, latitude + latSpan,
                longitude - lngSpan, longitude + lngSpan));
        if (!zones.isEmpty()) {
            candidates.addAll(repo.findWithoutPointInZones(ProductStatus.AVAILABLE, zones));
        }

        Set<Long> hidden = hiddenUserIds == null ? Set.of() : hiddenUserIds;
        double[] from = new double[] { latitude, longitude };
        int cap = boundedLimit(limit);

        List<ProductDTO> rows = dedupe(candidates).stream()
                .filter(p -> excludeUserId == null || p.getUser() == null
                        || !p.getUser().getId().equals(excludeUserId))
                .filter(p -> p.getUser() == null || !hidden.contains(p.getUser().getId()))
                // Le rectangle est plus large que le cercle qu'il englobe : la
                // distance exacte ecarte les coins.
                .filter(p -> distanceOrFar(p, from) <= radius)
                .sorted(Comparator.comparingDouble(p -> distanceOrFar(p, from)))
                .limit(cap)
                .map(p -> withDistance(toDTO(p, currentUserId, false), p, from))
                .collect(Collectors.toList());

        markFavorites(rows, currentUserId);
        return rows;
    }

    /** Les moughataas dont le centre tombe dans le rectangle. */
    private List<Moughataa> zonesWithin(double south, double north, double west, double east) {
        List<Moughataa> zones = new ArrayList<>();
        for (Moughataa zone : Moughataa.values()) {
            if (!zone.hasCentroid()) {
                continue;
            }
            double lat = zone.getCentroidLatitude();
            double lng = zone.getCentroidLongitude();
            if (lat >= south && lat <= north && lng >= west && lng <= east) {
                zones.add(zone);
            }
        }
        return zones;
    }

    /**
     * Les deux requetes peuvent rendre la meme annonce ; l'ordre d'arrivee est
     * conserve.
     */
    private List<Product> dedupe(List<Product> products) {
        Map<Long, Product> unique = new LinkedHashMap<>();
        for (Product p : products) {
            if (p.getId() != null) {
                unique.putIfAbsent(p.getId(), p);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private int boundedLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return MAP_DEFAULT_LIMIT;
        }
        return Math.min(limit, MAP_MAX_LIMIT);
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }
}
