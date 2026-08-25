package com.project.RecyConnect.Service;

import com.project.RecyConnect.Model.Negotiation;
import com.project.RecyConnect.Model.NegotiationStatus;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.NegotiationRepository;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * La mise en relation : qui obtient un numero, et qui n'en obtient pas.
 *
 * <p>C'est le remplacant de {@code GET /api/users/{id}}, par lequel tout compte
 * authentifie pouvait lire la fiche de n'importe quel autre — le point C3 de
 * l'audit. Ce qui est verrouille ici : un tiers n'obtient rien, une offre encore
 * en attente n'ouvre rien, et le vendeur d'une vieille offre sans destinataire
 * enregistre reste reconnu comme partie.
 */
@ExtendWith(MockitoExtension.class)
class NegotiationContactTest {

    @Mock private NegotiationRepository repo;
    @Mock private UserRepo userRepo;
    @Mock private ProductRepository productRepo;
    @Mock private NotificationService notificationService;
    @Mock private FileUrlService fileUrlService;

    @InjectMocks private NegotiationService service;

    private User acheteur;
    private User vendeur;
    private Product annonce;

    @BeforeEach
    void setUp() {
        acheteur = User.builder().id(1L).username("Ahmed").phone(22233445566L).role(Role.USER).build();
        vendeur = User.builder().id(2L).username("Fatima").phone(22277889900L).role(Role.USER).build();
        annonce = Product.builder().id(10L).title("Cartons").user(vendeur).build();
    }

    private Negotiation offre(String statut, User destinataire) {
        return Negotiation.builder()
                .id(100L)
                .sender(acheteur)
                .receiver(destinataire)
                .product(annonce)
                .status(statut)
                .price(50.0)
                .quantity(20)
                .build();
    }

    @Test
    @DisplayName("l'acheteur d'une offre acceptee obtient les deux numeros")
    void acheteurObtientLesNumeros() {
        when(repo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_ACCEPTED, vendeur)));

        NegotiationService.ContactLookup lookup = service.findContact(100L, acheteur.getId());

        assertEquals(NegotiationService.ContactAccess.GRANTED, lookup.access());
        assertEquals(1L, lookup.contact().getBuyerId());
        assertEquals(2L, lookup.contact().getSellerId());
        assertEquals("22233445566", lookup.contact().getBuyerPhone());
        assertEquals("22277889900", lookup.contact().getSellerPhone());
        assertEquals("Fatima", lookup.contact().getSellerUsername());
    }

    @Test
    @DisplayName("le vendeur aussi")
    void vendeurObtientLesNumeros() {
        when(repo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_ACCEPTED, vendeur)));

        NegotiationService.ContactLookup lookup = service.findContact(100L, vendeur.getId());

        assertEquals(NegotiationService.ContactAccess.GRANTED, lookup.access());
        assertEquals("22233445566", lookup.contact().getBuyerPhone());
    }

    @Test
    @DisplayName("un tiers authentifie n'obtient rien")
    void tiersRefuse() {
        when(repo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_ACCEPTED, vendeur)));

        NegotiationService.ContactLookup lookup = service.findContact(100L, 99L);

        assertEquals(NegotiationService.ContactAccess.NOT_A_PARTY, lookup.access());
        assertNull(lookup.contact());
    }

    @Test
    @DisplayName("une offre en attente n'ouvre aucun numero")
    void offreEnAttenteRefusee() {
        when(repo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_PENDING, vendeur)));

        NegotiationService.ContactLookup lookup = service.findContact(100L, acheteur.getId());

        assertEquals(NegotiationService.ContactAccess.NOT_ACCEPTED, lookup.access());
        assertNull(lookup.contact());
    }

    @Test
    @DisplayName("une offre refusee non plus")
    void offreRefuseeRefusee() {
        when(repo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_REJECTED, vendeur)));

        assertEquals(NegotiationService.ContactAccess.NOT_ACCEPTED,
                service.findContact(100L, acheteur.getId()).access());
    }

    @Test
    @DisplayName("l'appartenance est verifiee avant le statut : un tiers ne devine pas l'etat de l'offre")
    void tiersNeDistinguePasLeStatut() {
        when(repo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_PENDING, vendeur)));

        assertEquals(NegotiationService.ContactAccess.NOT_A_PARTY,
                service.findContact(100L, 99L).access());
    }

    @Test
    @DisplayName("offre inconnue")
    void offreInconnue() {
        when(repo.findById(404L)).thenReturn(Optional.empty());

        NegotiationService.ContactLookup lookup = service.findContact(404L, acheteur.getId());

        assertEquals(NegotiationService.ContactAccess.NOT_FOUND, lookup.access());
        assertNull(lookup.contact());
    }

    @Test
    @DisplayName("sans destinataire enregistre, le proprietaire de l'annonce fait office de vendeur")
    void vieilleOffreSansDestinataire() {
        when(repo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_ACCEPTED, null)));

        NegotiationService.ContactLookup lookup = service.findContact(100L, vendeur.getId());

        assertEquals(NegotiationService.ContactAccess.GRANTED, lookup.access());
        assertEquals(2L, lookup.contact().getSellerId());
        assertEquals("22277889900", lookup.contact().getSellerPhone());
    }

    @Test
    @DisplayName("un compte sans numero renseigne ne fait pas echouer la lecture")
    void numeroAbsent() {
        User sansNumero = User.builder().id(2L).username("Fatima").role(Role.USER).build();
        annonce.setUser(sansNumero);
        when(repo.findById(100L)).thenReturn(Optional.of(offre(NegotiationStatus.STATUS_ACCEPTED, sansNumero)));

        NegotiationService.ContactLookup lookup = service.findContact(100L, acheteur.getId());

        assertEquals(NegotiationService.ContactAccess.GRANTED, lookup.access());
        assertNull(lookup.contact().getSellerPhone());
        assertEquals("22233445566", lookup.contact().getBuyerPhone());
    }
}
