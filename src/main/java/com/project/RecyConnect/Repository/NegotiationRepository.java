package com.project.RecyConnect.Repository;


import com.project.RecyConnect.Model.Negotiation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NegotiationRepository extends JpaRepository<Negotiation, Long> {
    List<Negotiation> findBySenderId(Long senderId);
    List<Negotiation> findByReceiverId(Long receiverId);
    List<Negotiation> findByProductId(Long productId);
    List<Negotiation> findByProductIdAndStatusIn(Long productId, List<String> statuses);

    @Query("SELECT COALESCE(SUM(n.price * n.quantity), 0) FROM Negotiation n " +
           "WHERE n.product.user.id = :sellerId AND LOWER(n.status) = 'accepted'")
    Double sumAcceptedAmountBySellerId(@Param("sellerId") Long sellerId);

    @Query("SELECT COUNT(n) FROM Negotiation n " +
           "WHERE n.product.user.id = :sellerId AND LOWER(n.status) = 'accepted'")
    Long countAcceptedBySellerId(@Param("sellerId") Long sellerId);

    /** Les offres en attente sur une annonce — la preuve sociale de la fiche. */
    long countByProductIdAndStatusIgnoreCase(Long productId, String status);

    /**
     * L'appelant a-t-il une offre acceptee sur cette annonce ?
     *
     * <p>C'est ce qui lui ouvre la position exacte du lot. La regle est la meme
     * que pour l'echange des numeros : tant que rien n'est conclu, un vendeur
     * particulier n'a pas a livrer son adresse a tous ceux qui ouvrent sa
     * fiche.
     */
    boolean existsBySenderIdAndProductIdAndStatusIgnoreCase(Long senderId,
                                                            Long productId,
                                                            String status);

    /**
     * Le nombre d'offres qui attendent une reponse du vendeur.
     *
     * <p>Sert la pastille de l'onglet "Offres" de l'application. Elle etait
     * jusqu'ici deduite des notifications, faute de ce compte : une
     * notification perdue — permission refusee, application desinstallee un
     * temps — et le vendeur ne savait pas qu'on l'attendait. Une requete de
     * comptage, et le chiffre est exact.
     */
    @Query("SELECT COUNT(n) FROM Negotiation n "
            + "WHERE n.product.user.id = :sellerId AND LOWER(n.status) = 'pending'")
    long countPendingForSeller(@Param("sellerId") Long sellerId);

    /**
     * Le journal des transactions conclues d'un utilisateur, des deux cotes.
     *
     * <p>Un collecteur professionnel est tour a tour vendeur et acheteur; un
     * journal qui n'en montrerait qu'une moitie ne serait pas un outil de
     * gestion.
     */
    @Query("SELECT n FROM Negotiation n " +
           "WHERE LOWER(n.status) = 'accepted' " +
           "AND (n.sender.id = :userId OR n.product.user.id = :userId) " +
           "ORDER BY n.createdAt DESC")
    List<Negotiation> findAcceptedForUser(@Param("userId") Long userId);

    /**
     * La quantite totale detournee de la decharge par un vendeur.
     *
     * <p>Additionne les quantites reellement vendues, et non les quantites
     * publiees: une annonce sans preneur n'a rien recycle.
     */
    @Query("SELECT COALESCE(SUM(n.quantity), 0) FROM Negotiation n " +
           "WHERE n.product.user.id = :sellerId AND LOWER(n.status) = 'accepted'")
    Long sumAcceptedQuantityBySellerId(@Param("sellerId") Long sellerId);

    /**
     * Les offres qu'un vendeur a traitees — acceptees ou refusees — sur le
     * total de celles qu'il a recues. Sert le taux de reponse du profil
     * public: c'est ce qu'un acheteur veut savoir avant de proposer.
     */
    @Query("SELECT COUNT(n) FROM Negotiation n WHERE n.product.user.id = :sellerId")
    Long countReceivedBySellerId(@Param("sellerId") Long sellerId);

    @Query("SELECT COUNT(n) FROM Negotiation n WHERE n.product.user.id = :sellerId " +
           "AND LOWER(n.status) IN ('accepted', 'rejected')")
    Long countAnsweredBySellerId(@Param("sellerId") Long sellerId);
}
