package com.project.RecyConnect.Service;

import com.project.RecyConnect.Model.PhoneVerification;
import com.project.RecyConnect.Repository.PhoneVerificationRepository;
import com.project.RecyConnect.Repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verrouille les garanties de securite du flux de verification par SMS.
 *
 * Ces tests couvrent des regressions reelles corrigees dans la revue de code:
 * un code OTP rejouable et une validation de numero incoherente.
 */
@ExtendWith(MockitoExtension.class)
class PhoneVerificationServiceTest {

    private static final Long PHONE = 12345678L;
    private static final String CODE = "123456";

    @Mock
    private PhoneVerificationRepository repo;

    @Mock
    private UserRepo userRepo;

    @InjectMocks
    private PhoneVerificationService service;

    private PhoneVerification codeValide;

    @BeforeEach
    void setUp() {
        codeValide = PhoneVerification.builder()
                .id(1L)
                .phone(PHONE)
                .code(CODE)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Un code valide est consomme: il ne peut pas etre rejoue")
    void consumeCode_supprimeLeCodeApresUsage() {
        when(repo.findTopByPhoneAndCodeOrderByCreatedAtDesc(PHONE, CODE))
                .thenReturn(Optional.of(codeValide));

        assertTrue(service.consumeCode(PHONE, CODE));

        // La suppression est ce qui empeche le rejeu du meme code.
        verify(repo, times(1)).delete(codeValide);
    }

    @Test
    @DisplayName("Un code inconnu est refuse et ne declenche aucune suppression")
    void consumeCode_refuseUnCodeInconnu() {
        when(repo.findTopByPhoneAndCodeOrderByCreatedAtDesc(anyLong(), anyString()))
                .thenReturn(Optional.empty());

        assertFalse(service.consumeCode(PHONE, "000000"));

        verify(repo, never()).delete(any());
    }

    @Test
    @DisplayName("Un code de plus de 10 minutes est refuse")
    void consumeCode_refuseUnCodeExpire() {
        codeValide.setCreatedAt(OffsetDateTime.now().minusMinutes(11));
        when(repo.findTopByPhoneAndCodeOrderByCreatedAtDesc(PHONE, CODE))
                .thenReturn(Optional.of(codeValide));

        assertFalse(service.consumeCode(PHONE, CODE));
    }

    @Test
    @DisplayName("verifyCodeBeforeRegistration ne consomme PAS le code")
    void verifyCode_neConsommePasLeCode() {
        when(repo.findTopByPhoneAndCodeOrderByCreatedAtDesc(PHONE, CODE))
                .thenReturn(Optional.of(codeValide));

        assertTrue(service.verifyCodeBeforeRegistration(PHONE, CODE));

        // L'etape intermediaire /verify-code doit laisser le code utilisable
        // par /register, qui est l'etape qui accorde reellement l'acces.
        verify(repo, never()).delete(any());
    }

    @Test
    @DisplayName("Un code juste sous la limite d'expiration reste accepte")
    void consumeCode_accepteUnCodeALaLimite() {
        codeValide.setCreatedAt(OffsetDateTime.now().minusMinutes(9));
        when(repo.findTopByPhoneAndCodeOrderByCreatedAtDesc(PHONE, CODE))
                .thenReturn(Optional.of(codeValide));

        assertTrue(service.consumeCode(PHONE, CODE));
    }
}
