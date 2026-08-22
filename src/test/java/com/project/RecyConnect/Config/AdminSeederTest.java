package com.project.RecyConnect.Config;

import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.UserRepo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verrouille le comportement du seeder d'administrateur: creation unique,
 * mot de passe hache, et aucune ecriture quand le compte existe deja.
 */
@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

    private static final String USERNAME = "admin";
    private static final Long PHONE = 22222222L;
    private static final String PASSWORD = "admin123";

    @Mock
    private UserRepo userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AdminSeeder seeder(boolean enabled, String password) {
        return new AdminSeeder(userRepository, passwordEncoder, enabled, USERNAME, PHONE, password);
    }

    @Test
    @DisplayName("Cree l'administrateur avec un mot de passe hache sur une base vide")
    void createsAdminWhenAbsent() {
        when(userRepository.findByPhone(PHONE)).thenReturn(null);
        when(userRepository.findByUsername(USERNAME)).thenReturn(null);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        seeder(true, PASSWORD).run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertEquals(USERNAME, saved.getUsername());
        assertEquals(PHONE, saved.getPhone());
        assertEquals(Role.ADMIN, saved.getRole());
        assertNotEquals(PASSWORD, saved.getPwd(), "Le mot de passe ne doit jamais etre stocke en clair");
        assertTrue(passwordEncoder.matches(PASSWORD, saved.getPwd()));
    }

    @Test
    @DisplayName("Ne recree rien si le numero est deja utilise")
    void skipsWhenPhoneAlreadyTaken() {
        when(userRepository.findByPhone(PHONE))
                .thenReturn(User.builder().id(1L).username("someone").phone(PHONE).role(Role.USER).build());

        seeder(true, PASSWORD).run(null);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Ne recree rien si le nom d'utilisateur est deja pris")
    void skipsWhenUsernameAlreadyTaken() {
        when(userRepository.findByPhone(PHONE)).thenReturn(null);
        when(userRepository.findByUsername(USERNAME))
                .thenReturn(User.builder().id(2L).username(USERNAME).phone(44556677L).role(Role.ADMIN).build());

        seeder(true, PASSWORD).run(null);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Ne touche pas a la base quand le seeder est desactive")
    void doesNothingWhenDisabled() {
        seeder(false, PASSWORD).run(null);

        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).findByPhone(any());
    }

    @Test
    @DisplayName("Refuse de creer un admin sans mot de passe configure")
    void doesNothingWithoutPassword() {
        seeder(true, "").run(null);

        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).findByPhone(any());
    }
}
