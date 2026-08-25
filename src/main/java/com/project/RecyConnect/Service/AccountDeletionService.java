package com.project.RecyConnect.Service;

import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * La suppression definitive d'un compte, et de tout ce qui s'y rattache.
 *
 * <p>Le reglement "Donnees utilisateur" de Google Play impose que la
 * suppression demandee depuis l'application efface reellement les donnees, pas
 * seulement la ligne du compte. Le simple {@code userRepository.deleteById}
 * qui tenait lieu de suppression n'y suffisait pas : la cascade de l'entite
 * {@code User} ne couvre ni les notifications, ni les sessions, ni les codes
 * SMS — trois tables qui referencent {@code users} et dont la contrainte
 * d'integrite faisait echouer la requete des que le compte avait servi — et
 * elle laissait sur le disque les photos des annonces, encore accessibles par
 * leur URL.
 *
 * <p>L'ordre ci-dessous suit les dependances : on retire ce qui pointe vers le
 * compte avant le compte lui-meme.
 *
 * <p>Les signalements font exception : ils sont detaches, pas effaces. C'est ce
 * que la politique de confidentialite annonce — la trace d'un signalement
 * traite survit au compte, mais ne designe plus personne.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepo userRepo;
    private final ProductRepository productRepo;
    private final NegotiationRepository negotiationRepo;
    private final NotificationRepository notificationRepo;
    private final PhoneVerificationRepository phoneVerificationRepo;
    private final UserSessionRepository sessionRepo;
    private final UserBlockRepository blockRepo;
    private final ReportRepository reportRepo;
    private final UploadedFileStore fileStore;

    public AccountDeletionService(UserRepo userRepo,
                                  ProductRepository productRepo,
                                  NegotiationRepository negotiationRepo,
                                  NotificationRepository notificationRepo,
                                  PhoneVerificationRepository phoneVerificationRepo,
                                  UserSessionRepository sessionRepo,
                                  UserBlockRepository blockRepo,
                                  ReportRepository reportRepo,
                                  UploadedFileStore fileStore) {
        this.userRepo = userRepo;
        this.productRepo = productRepo;
        this.negotiationRepo = negotiationRepo;
        this.notificationRepo = notificationRepo;
        this.phoneVerificationRepo = phoneVerificationRepo;
        this.sessionRepo = sessionRepo;
        this.blockRepo = blockRepo;
        this.reportRepo = reportRepo;
        this.fileStore = fileStore;
    }

    /**
     * Efface le compte {@code userId} et tout ce qui s'y rattache.
     *
     * @throws IllegalArgumentException si le compte n'existe pas.
     */
    @Transactional
    public void deleteAccount(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Long phone = user.getPhone();

        // 1. Signalements : detaches, jamais effaces.
        reportRepo.detachReporter(userId);
        reportRepo.detachHandler(userId);

        // 2. Blocages, dans les deux sens.
        blockRepo.deleteAllInvolving(userId);

        // 3. Notifications envoyees et recues.
        notificationRepo.deleteAll(notificationRepo.findBySenderId(userId));
        notificationRepo.deleteAll(notificationRepo.findByReceiverId(userId));

        // 4. Codes SMS : par compte et par numero — un code peut avoir ete
        //    demande avant que le compte n'existe.
        phoneVerificationRepo.findByUserId(userId).ifPresent(phoneVerificationRepo::delete);
        if (phone != null) {
            phoneVerificationRepo.deleteAll(phoneVerificationRepo.findByPhoneOrderByCreatedAtDesc(phone));
        }

        // 5. Offres emises et recues.
        negotiationRepo.deleteAll(negotiationRepo.findBySenderId(userId));
        negotiationRepo.deleteAll(negotiationRepo.findByReceiverId(userId));

        // 6. Les annonces, et d'abord les offres qui les visent : une offre
        //    dont le produit disparait romprait la contrainte d'integrite.
        List<Product> products = productRepo.findByUserId(userId);
        List<String> imageUrls = new ArrayList<>();
        for (Product product : products) {
            negotiationRepo.deleteAll(negotiationRepo.findByProductId(product.getId()));
            if (product.getImageUrls() != null) {
                imageUrls.addAll(product.getImageUrls());
            }
        }
        productRepo.deleteAll(products);

        // 7. Session ouverte, donc jeton en cours de validite.
        sessionRepo.findById(userId).ifPresent(sessionRepo::delete);

        // 8. Le compte lui-meme.
        userRepo.deleteById(userId);

        // 9. Les photos, une fois la transaction validee seulement : effacees
        //    avant, un echec en fin de course les aurait perdues alors que les
        //    annonces, elles, seraient restees.
        deleteFilesAfterCommit(imageUrls, userId);
    }

    private void deleteFilesAfterCommit(List<String> imageUrls, Long userId) {
        if (imageUrls.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fileStore.deleteAllByPublicUrl(imageUrls);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                int deleted = fileStore.deleteAllByPublicUrl(imageUrls);
                log.info("Compte {} supprime: {}/{} photos effacees du disque",
                        userId, deleted, imageUrls.size());
            }
        });
    }
}
