package com.project.RecyConnect.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.project.RecyConnect.DTO.NotificationPreferencesDTO;
import com.project.RecyConnect.DTO.PublicUserDTO;
import com.project.RecyConnect.DTO.UserDTO;
import com.project.RecyConnect.DTO.UserStatsDTO;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.ProductStatus;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.SupportedLanguage;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.NegotiationRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.ReviewRepository;
import com.project.RecyConnect.Repository.UserRepo;

@Service
public class UserService implements UserDetailsService {
    private final UserRepo userRepository;
    private final ProductRepository productRepository;
    private final NegotiationRepository negotiationRepository;
    private final ReviewRepository reviewRepository;

    public UserService(UserRepo userRepository, ProductRepository productRepository,
                       NegotiationRepository negotiationRepository,
                       ReviewRepository reviewRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.negotiationRepository = negotiationRepository;
        this.reviewRepository = reviewRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User optionalUser = userRepository.findByUsername(username);
        if(optionalUser==null) throw new UsernameNotFoundException("Username not found", null);
        // Retourner l'entite User: elle implemente UserDetails et expose les autorites
        // (ROLE_USER / ROLE_ADMIN) necessaires aux controles hasRole(...) et @PreAuthorize.
        return optionalUser;
    }

    private UserDTO toDTO(User u) {
        UserDTO dto = new UserDTO();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setPhone(u.getPhone());
        dto.setImageData(u.getImageData());
        dto.setRole(u.getRole());
        dto.setCreatedAt(u.getCreatedAt());
        dto.setPreferredLanguage(SupportedLanguage.of(u).getCode());
        return dto;
    }

    private User fromDTO(UserDTO dto) {
        return User.builder()
                .id(dto.getId())
                .username(dto.getUsername())
                .phone(dto.getPhone())
                .imageData(dto.getImageData())
                .build();
    }

    public List<UserDTO> findAll() {
        return userRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public Optional<UserDTO> findById(Long id) {
        return userRepository.findById(id).map(this::toDTO);
    }

    /** Vrai si un compte porte deja ce numero de telephone. */
    public boolean phoneExists(Long phone) {
        return phone != null && userRepository.findByPhone(phone) != null;
    }

    /** Vrai si un compte porte deja ce nom d'utilisateur. */
    public boolean usernameExists(String username) {
        return username != null && userRepository.findByUsername(username) != null;
    }

    /**
     * Cree un compte depuis le panneau d'administration.
     *
     * <p>Contrairement a l'inscription mobile, aucun code SMS n'est envoye:
     * l'administrateur choisit le mot de passe et le compte est utilisable
     * immediatement. C'est l'appelant qui verifie l'unicite du numero et du
     * nom, et qui a deja le role ADMIN.
     *
     * <p>Le mot de passe arrive <em>deja hache</em>. Le hachage reste au
     * controleur parce qu'y injecter le {@code PasswordEncoder} ici fermerait
     * un cycle de dependances: WebSecurityConfiguration declare ce bean et
     * exige JwtRequestFilter, qui exige a son tour ce service.
     */
    public UserDTO createAccount(String username, Long phone, String encodedPassword, Role role) {
        User user = User.builder()
                .username(username)
                .phone(phone)
                .pwd(encodedPassword)
                .role(role == null ? Role.USER : role)
                .imageData(User.DEFAULT_IMAGE_DATA)
                .build();
        return toDTO(userRepository.save(user));
    }

    /**
     * Remplace le mot de passe d'un compte, deja hache par l'appelant.
     *
     * <p>Sert a la reinitialisation par un administrateur quand un utilisateur
     * ne recoit plus ses SMS. Revoquer ses sessions est la responsabilite de
     * l'appelant, qui seul sait s'il faut deconnecter le compte.
     */
    public void setPassword(Long id, String encodedPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPwd(encodedPassword);
        userRepository.save(user);
    }

    public UserDTO update(Long id, UserDTO dto) {
        return userRepository.findById(id).map(existing -> {
            if (dto.getUsername() != null) existing.setUsername(dto.getUsername());
            if (dto.getPhone() != null) existing.setPhone(dto.getPhone());
            if (dto.getImageData() != null) existing.setImageData(dto.getImageData());
            User saved = userRepository.save(existing);
            return toDTO(saved);
        }).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public UserDTO patch(Long id, UserDTO dto) {
        return userRepository.findById(id).map(existing -> {
            if (dto.getUsername() != null) existing.setUsername(dto.getUsername());
            if (dto.getPhone() != null) existing.setPhone(dto.getPhone());
            if (dto.getImageData() != null) existing.setImageData(dto.getImageData());
            User saved = userRepository.save(existing);
            return toDTO(saved);
        }).orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * Met à jour un utilisateur et retourne l'entité User (pour la génération d'un nouveau token JWT)
     */
    public User patchAndGetUser(Long id, UserDTO dto) {
        return userRepository.findById(id).map(existing -> {
            if (dto.getUsername() != null) existing.setUsername(dto.getUsername());
            if (dto.getPhone() != null) existing.setPhone(dto.getPhone());
            if (dto.getImageData() != null) existing.setImageData(dto.getImageData());
            return userRepository.save(existing);
        }).orElseThrow(() -> new RuntimeException("User not found"));
    }

    // `delete(Long)` a ete retiree: un `deleteById` ne suffit pas a supprimer un
    // compte. Notifications, sessions et codes SMS referencent `users` sans
    // cascade, et la contrainte d'integrite faisait echouer la requete des que
    // le compte avait servi ; les photos des annonces, elles, restaient sur le
    // disque. La suppression complete vit dans AccountDeletionService.

    public Optional<UserDTO> findByPhone(Long phone) {
        User user = userRepository.findByPhone(phone);
        if (user == null) {
            return Optional.empty();
        }
        return Optional.of(toDTO(user));
    }

    public Optional<UserStatsDTO> getUserStats(Long userId) {
        return userRepository.findById(userId).map(user -> {
            List<Product> userProducts = productRepository.findByUserId(userId);
            List<Product> nonArchivedProducts = userProducts.stream()
                .filter(p -> p.getStatus() != ProductStatus.ARCHIVED)
                .collect(Collectors.toList());
            
            int totalProducts = nonArchivedProducts.size();
            int recycledCount = (int) nonArchivedProducts.stream()
                    .filter(p -> p.getStatus() == ProductStatus.RECYCLED)
                    .count();
            int availableCount = (int) nonArchivedProducts.stream()
                    .filter(p -> p.getStatus() == ProductStatus.AVAILABLE)
                    .count();
            
            String recyclingRate = "0%";
            if (totalProducts > 0) {
                double rate = (recycledCount * 100.0) / totalProducts;
                recyclingRate = String.format("%.1f%%", rate);
            }
            
            UserStatsDTO statsDTO = new UserStatsDTO();
            statsDTO.setUserId(userId);
            statsDTO.setTotalProducts(totalProducts);
            statsDTO.setRecycledCount(recycledCount);
            statsDTO.setAvailableCount(availableCount);
            statsDTO.setRecyclingRate(recyclingRate);
            Long recycledQuantity = negotiationRepository.sumAcceptedQuantityBySellerId(userId);
            statsDTO.setRecycledQuantity(recycledQuantity != null ? recycledQuantity : 0L);
            statsDTO.setMemberSince(user.getCreatedAt());

            return statsDTO;
        });
    }

    /**
     * La fiche publique d'un vendeur: ce qu'un acheteur a le droit de savoir
     * avant de proposer une offre.
     *
     * <p>Ni numero, ni role, ni rien qui permette de remonter au compte:
     * c'est tout l'objet d'un DTO distinct de {@link UserDTO}.
     */
    public Optional<PublicUserDTO> getPublicProfile(Long userId) {
        return userRepository.findById(userId).map(user -> {
            List<Product> visible = productRepository.findByUserId(userId).stream()
                    .filter(p -> p.getStatus() != ProductStatus.ARCHIVED)
                    .collect(Collectors.toList());

            PublicUserDTO dto = new PublicUserDTO();
            dto.setId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setImageData(user.getImageData());
            dto.setMemberSince(user.getCreatedAt());
            dto.setPublishedCount(visible.size());
            dto.setRecycledCount((int) visible.stream()
                    .filter(p -> p.getStatus() == ProductStatus.RECYCLED)
                    .count());

            Long accepted = negotiationRepository.countAcceptedBySellerId(userId);
            dto.setCompletedDeals(accepted != null ? accepted : 0L);

            Long received = negotiationRepository.countReceivedBySellerId(userId);
            Long answered = negotiationRepository.countAnsweredBySellerId(userId);
            // Nul plutot que 0 % tant qu'aucune offre n'est arrivee: il n'y a
            // rien a mesurer, et un 0 % se lirait comme un mauvais eleve.
            if (received != null && received > 0) {
                dto.setResponseRate((int) Math.round(
                        ((answered != null ? answered : 0L) * 100.0) / received));
            }

            dto.setReviewAverage(reviewRepository.averageRatingByTargetId(userId));
            dto.setReviewCount(reviewRepository.countByTargetId(userId));
            return dto;
        });
    }

    /**
     * Les preferences de notification d'un compte.
     *
     * <p>Un choix jamais exprime vaut consentement — c'etait le comportement
     * avant l'ajout des colonnes, et le changer couperait sans preavis les
     * notifications de tous les comptes existants.
     */
    public NotificationPreferencesDTO getNotificationPreferences(Long userId) {
        return userRepository.findById(userId)
                .map(UserService::toPreferences)
                .orElseGet(UserService::defaultPreferences);
    }

    public Optional<NotificationPreferencesDTO> updateNotificationPreferences(
            Long userId, NotificationPreferencesDTO dto) {
        return userRepository.findById(userId).map(user -> {
            if (dto.getOffers() != null) user.setNotifyOffers(dto.getOffers());
            if (dto.getSystem() != null) user.setNotifySystem(dto.getSystem());
            if (dto.getPromotions() != null) user.setNotifyPromotions(dto.getPromotions());
            return toPreferences(userRepository.save(user));
        });
    }

    private static NotificationPreferencesDTO toPreferences(User user) {
        NotificationPreferencesDTO dto = new NotificationPreferencesDTO();
        dto.setOffers(user.getNotifyOffers() == null || user.getNotifyOffers());
        dto.setSystem(user.getNotifySystem() == null || user.getNotifySystem());
        dto.setPromotions(user.getNotifyPromotions() == null || user.getNotifyPromotions());
        return dto;
    }

    private static NotificationPreferencesDTO defaultPreferences() {
        NotificationPreferencesDTO dto = new NotificationPreferencesDTO();
        dto.setOffers(true);
        dto.setSystem(true);
        dto.setPromotions(true);
        return dto;
    }
    
    /**
     * Change la langue dans laquelle un compte recoit ses notifications.
     *
     * <p>Prend un {@link SupportedLanguage} et non une chaine: la validation de
     * ce que le client a envoye appartient au point d'entree HTTP, qui seul
     * peut en faire un 400 intelligible. Ici, la langue est deja connue comme
     * valide et il ne reste qu'a l'ecrire.
     */
    public UserDTO updatePreferredLanguage(Long userId, SupportedLanguage language) {
        return userRepository.findById(userId).map(user -> {
            user.setPreferredLanguage(language.getCode());
            return toDTO(userRepository.save(user));
        }).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public void updateFcmToken(Long userId, String fcmToken) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFcmToken(fcmToken);
            userRepository.save(user);
        });
    }

    /**
     * Met à jour le rôle d'un utilisateur
     * @param userId L'ID de l'utilisateur
     * @param role Le nouveau rôle (USER ou ADMIN)
     * @return L'utilisateur mis à jour
     */
    public UserDTO updateRole(Long userId, Role role) {
        return userRepository.findById(userId).map(user -> {
            user.setRole(role);
            User saved = userRepository.save(user);
            return toDTO(saved);
        }).orElseThrow(() -> new RuntimeException("User not found"));
    }
    
    /**
     * Récupère l'utilisateur actuellement authentifié depuis le SecurityContext
     * @return L'utilisateur connecté ou null si non authentifié
     */
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof UserDetails) {
            String username = ((UserDetails) authentication.getPrincipal()).getUsername();
            return userRepository.findByUsername(username);
        }
        return null;
    }
}