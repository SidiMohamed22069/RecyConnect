package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.SearchAlertDTO;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.ProductStatus;
import com.project.RecyConnect.Model.SearchAlert;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.CategoryRepository;
import com.project.RecyConnect.Repository.SearchAlertRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Les veilles de recherche et leur declenchement.
 *
 * <p>Le canal de notification etait deja en place; il ne manquait que la
 * regle. Une veille se declenche a la publication d'une annonce, jamais
 * retroactivement: prevenir d'une annonce vieille de trois semaines au moment
 * ou l'on cree l'alerte serait du bruit.
 */
@Service
public class SearchAlertService {

    private final SearchAlertRepository repo;
    private final UserRepo userRepo;
    private final CategoryRepository categoryRepo;
    private final NotificationService notificationService;

    public SearchAlertService(SearchAlertRepository repo, UserRepo userRepo,
                              CategoryRepository categoryRepo,
                              NotificationService notificationService) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.categoryRepo = categoryRepo;
        this.notificationService = notificationService;
    }

    private SearchAlertDTO toDTO(SearchAlert alert) {
        SearchAlertDTO dto = new SearchAlertDTO();
        dto.setId(alert.getId());
        dto.setCreatedAt(alert.getCreatedAt());
        dto.setUserId(alert.getUser() != null ? alert.getUser().getId() : null);
        dto.setKeyword(alert.getKeyword());
        dto.setCategoryId(alert.getCategory() != null ? alert.getCategory().getId() : null);
        dto.setCategoryName(alert.getCategory() != null ? alert.getCategory().getName() : null);
        dto.setMaxPrice(alert.getMaxPrice());
        dto.setMinQuantity(alert.getMinQuantity());
        dto.setLocation(alert.getLocation());
        dto.setActive(alert.getActive() == null || alert.getActive());
        return dto;
    }

    @Transactional(readOnly = true)
    public List<SearchAlertDTO> findByUser(Long userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public SearchAlertDTO create(Long userId, SearchAlertDTO dto) {
        User owner = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        SearchAlert alert = new SearchAlert();
        alert.setUser(owner);
        apply(alert, dto);
        return toDTO(repo.save(alert));
    }

    @Transactional
    public Optional<SearchAlertDTO> update(Long alertId, Long userId, SearchAlertDTO dto) {
        return repo.findById(alertId)
                .filter(alert -> alert.getUser() != null && alert.getUser().getId().equals(userId))
                .map(alert -> {
                    apply(alert, dto);
                    return toDTO(repo.save(alert));
                });
    }

    @Transactional
    public boolean delete(Long alertId, Long userId) {
        return repo.findById(alertId)
                .filter(alert -> alert.getUser() != null && alert.getUser().getId().equals(userId))
                .map(alert -> {
                    repo.delete(alert);
                    return true;
                })
                .orElse(false);
    }

    private void apply(SearchAlert alert, SearchAlertDTO dto) {
        alert.setKeyword(blankToNull(dto.getKeyword()));
        alert.setMaxPrice(dto.getMaxPrice());
        alert.setMinQuantity(dto.getMinQuantity());
        alert.setLocation(dto.getLocation());
        alert.setActive(dto.getActive() == null || dto.getActive());
        if (dto.getCategoryId() != null) {
            categoryRepo.findById(dto.getCategoryId()).ifPresent(alert::setCategory);
        } else {
            alert.setCategory(null);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Previent les veilles que satisfait une annonce qui vient de paraitre.
     *
     * <p>L'auteur de l'annonce n'est jamais prevenu de sa propre publication,
     * meme si elle correspond a l'une de ses veilles.
     *
     * <p>Une veille en echec ne doit pas faire echouer la publication: c'est
     * l'annonce qui compte, la notification n'est qu'un service rendu.
     */
    @Transactional(readOnly = true)
    public void notifyMatching(Product product) {
        if (product == null || product.getStatus() != ProductStatus.AVAILABLE) {
            return;
        }
        Long ownerId = product.getUser() != null ? product.getUser().getId() : null;

        for (SearchAlert alert : repo.findByActiveTrue()) {
            Long watcherId = alert.getUser() != null ? alert.getUser().getId() : null;
            if (watcherId == null || watcherId.equals(ownerId) || !matches(alert, product)) {
                continue;
            }
            try {
                notificationService.sendLocalizedNotification(
                        watcherId,
                        ownerId,
                        product.getId(),
                        "SEARCH_ALERT",
                        product.getTitle()
                );
            } catch (RuntimeException e) {
                // Volontairement avale: une veille muette est un desagrement,
                // une publication perdue est une regression.
            }
        }
    }

    private boolean matches(SearchAlert alert, Product product) {
        if (alert.getCategory() != null) {
            if (product.getCategory() == null
                    || !alert.getCategory().getId().equals(product.getCategory().getId())) {
                return false;
            }
        }
        if (alert.getKeyword() != null) {
            String needle = alert.getKeyword().toLowerCase();
            boolean inTitle = product.getTitle() != null
                    && product.getTitle().toLowerCase().contains(needle);
            boolean inDescription = product.getDescription() != null
                    && product.getDescription().toLowerCase().contains(needle);
            if (!inTitle && !inDescription) {
                return false;
            }
        }
        if (alert.getMaxPrice() != null
                && (product.getPrice() == null || product.getPrice() > alert.getMaxPrice())) {
            return false;
        }
        if (alert.getMinQuantity() != null
                && (product.getQuantityAvailable() == null
                    || product.getQuantityAvailable() < alert.getMinQuantity())) {
            return false;
        }
        return alert.getLocation() == null || alert.getLocation() == product.getLocation();
    }
}
