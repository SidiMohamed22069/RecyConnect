package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.ProductDTO;
import com.project.RecyConnect.Model.Favorite;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.ProductStatus;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.FavoriteRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Les annonces qu'un utilisateur a mises de cote.
 *
 * <p>Le stockage est cote serveur, et non dans les preferences de l'appareil:
 * un acheteur qui change de telephone, ou qui consulte depuis deux appareils,
 * doit retrouver sa liste. C'est aussi ce qui permettra plus tard de le
 * prevenir qu'un lot enregistre baisse de prix.
 */
@Service
public class FavoriteService {

    private final FavoriteRepository repo;
    private final ProductRepository productRepo;
    private final UserRepo userRepo;
    private final ProductService productService;

    public FavoriteService(FavoriteRepository repo, ProductRepository productRepo,
                           UserRepo userRepo, ProductService productService) {
        this.repo = repo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.productService = productService;
    }

    /**
     * Les annonces enregistrees, la plus recemment ajoutee en tete.
     *
     * <p>Les annonces supprimees depuis (archivees) disparaissent de la liste
     * plutot que d'y laisser une ligne morte, mais celles qui sont vendues ou
     * en pause restent visibles: savoir qu'un lot suivi est parti fait partie
     * de l'information.
     */
    @Transactional(readOnly = true)
    public List<ProductDTO> findByUser(Long userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(Favorite::getProduct)
                .filter(product -> product != null && product.getStatus() != ProductStatus.ARCHIVED)
                .map(product -> {
                    ProductDTO dto = productService.toPublicDTO(product);
                    dto.setFavorite(true);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Enregistre une annonce. Deux appuis sur le coeur ne creent qu'une ligne:
     * l'operation est volontairement idempotente, le client n'a pas a savoir
     * s'il ajoute ou s'il confirme.
     */
    @Transactional
    public boolean add(Long userId, Long productId) {
        if (repo.existsByUserIdAndProductId(userId, productId)) {
            return true;
        }
        User user = userRepo.findById(userId).orElse(null);
        Product product = productRepo.findById(productId).orElse(null);
        if (user == null || product == null) {
            return false;
        }
        repo.save(Favorite.builder().user(user).product(product).build());
        return true;
    }

    @Transactional
    public void remove(Long userId, Long productId) {
        repo.deleteByUserIdAndProductId(userId, productId);
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(Long userId, Long productId) {
        return repo.existsByUserIdAndProductId(userId, productId);
    }
}
