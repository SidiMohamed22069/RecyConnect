package com.project.RecyConnect.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.project.RecyConnect.Model.Role;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.UserRepo;

/**
 * Cree le compte administrateur d'amorcage au demarrage.
 *
 * Sans lui, une base neuve ne peut contenir aucun administrateur:
 * /api/auth/register-admin exige deja un appelant authentifie ayant le role ADMIN,
 * et /api/auth/register cree toujours un USER.
 *
 * Le seeder est idempotent: il ne cree rien si le numero ou le nom d'utilisateur
 * est deja pris, et ne modifie jamais un compte existant.
 *
 * Il est actif par defaut en local et desactive par defaut en production
 * (voir application-prod.properties): un administrateur au mot de passe connu
 * ne doit pas apparaitre tout seul sur un environnement expose.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepo userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String username;
    private final Long phone;
    private final String password;

    public AdminSeeder(
            UserRepo userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin-seed.enabled:true}") boolean enabled,
            @Value("${app.admin-seed.username:admin}") String username,
            @Value("${app.admin-seed.phone:22222222}") Long phone,
            @Value("${app.admin-seed.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.username = username;
        this.phone = phone;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        if (password == null || password.isBlank()) {
            log.warn("Seed admin ignore: app.admin-seed.password est vide. "
                    + "Renseigner ADMIN_SEED_PASSWORD ou desactiver app.admin-seed.enabled.");
            return;
        }

        // Le numero est la cle de connexion (POST /api/auth/login): un doublon
        // rendrait findByPhone non deterministe.
        User existingByPhone = userRepository.findByPhone(phone);
        if (existingByPhone != null) {
            log.info("Seed admin ignore: le numero {} appartient deja a '{}' (role {}).",
                    phone, existingByPhone.getUsername(), existingByPhone.getRole());
            return;
        }

        // findByUsername alimente loadUserByUsername: un doublon casserait
        // l'authentification de tous les comptes homonymes.
        User existingByUsername = userRepository.findByUsername(username);
        if (existingByUsername != null) {
            log.warn("Seed admin ignore: le nom d'utilisateur '{}' est deja pris (numero {}).",
                    username, existingByUsername.getPhone());
            return;
        }

        User admin = User.builder()
                .username(username)
                .pwd(passwordEncoder.encode(password))
                .phone(phone)
                .role(Role.ADMIN)
                .imageData(User.DEFAULT_IMAGE_DATA)
                .build();

        User saved = userRepository.save(admin);

        log.info("Administrateur d'amorcage cree: id={}, username='{}', phone={}. "
                        + "Se connecter avec phone={} (POST /api/auth/login), puis changer le mot de passe.",
                saved.getId(), saved.getUsername(), saved.getPhone(), loginPhone(saved.getPhone()));
    }

    /**
     * Numero a envoyer a /api/auth/login pour atteindre le compte enregistre.
     *
     * Le controleur retire systematiquement un prefixe "222" avant de chercher en base.
     * Un numero local qui commence lui-meme par 222 (cas de 22222222) serait donc
     * tronque et introuvable s'il etait envoye tel quel: on prefixe toujours par 222,
     * forme internationale qui redonne le numero stocke apres troncature.
     */
    private static String loginPhone(Long storedPhone) {
        return "222" + storedPhone;
    }
}
