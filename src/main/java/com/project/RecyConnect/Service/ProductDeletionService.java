package com.project.RecyConnect.Service;

import com.project.RecyConnect.Model.Negotiation;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Repository.FavoriteRepository;
import com.project.RecyConnect.Repository.NegotiationHistoryRepository;
import com.project.RecyConnect.Repository.NegotiationRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Supprime une annonce et tout ce qui la reference.
 *
 * <p>Un simple {@code deleteById} rompait la contrainte d'integrite des que
 * l'annonce avait recu une offre (negotiations, puis leur historique et leurs
 * avis) ou avait ete mise en favori. L'ordre suit les dependances.
 */
@Service
public class ProductDeletionService {

    private final ProductRepository productRepo;
    private final NegotiationRepository negotiationRepo;
    private final NegotiationHistoryRepository historyRepo;
    private final ReviewRepository reviewRepo;
    private final FavoriteRepository favoriteRepo;

    public ProductDeletionService(ProductRepository productRepo,
                                  NegotiationRepository negotiationRepo,
                                  NegotiationHistoryRepository historyRepo,
                                  ReviewRepository reviewRepo,
                                  FavoriteRepository favoriteRepo) {
        this.productRepo = productRepo;
        this.negotiationRepo = negotiationRepo;
        this.historyRepo = historyRepo;
        this.reviewRepo = reviewRepo;
        this.favoriteRepo = favoriteRepo;
    }

    /** @return {@code false} si l'annonce n'existe pas. */
    @Transactional
    public boolean delete(Long productId) {
        Product product = productRepo.findById(productId).orElse(null);
        if (product == null) {
            return false;
        }
        deleteDependents(List.of(product));
        productRepo.delete(product);
        return true;
    }

    /** Efface ce qui pointe vers ces annonces, sans les annonces elles-memes. */
    @Transactional
    public void deleteDependents(List<Product> products) {
        if (products.isEmpty()) {
            return;
        }
        List<Long> productIds = products.stream().map(Product::getId).toList();
        favoriteRepo.deleteByProductIdIn(productIds);
        for (Long productId : productIds) {
            List<Negotiation> offers = negotiationRepo.findByProductId(productId);
            if (offers.isEmpty()) {
                continue;
            }
            List<Long> offerIds = offers.stream().map(Negotiation::getId).toList();
            historyRepo.deleteByNegotiationIdIn(offerIds);
            reviewRepo.deleteByNegotiationIdIn(offerIds);
            negotiationRepo.deleteAll(offers);
        }
    }
}
