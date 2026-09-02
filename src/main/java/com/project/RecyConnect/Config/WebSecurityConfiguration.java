package com.project.RecyConnect.Config;


import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class WebSecurityConfiguration {

    private final JwtRequestFilter authFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // API stateless authentifiee par JWT: pas de cookie de session, donc pas de CSRF.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // --- Endpoints admin (declares AVANT les regles plus larges) ---
                        .requestMatchers("/api/auth/register-admin").hasRole("ADMIN")
                        .requestMatchers("/api/products/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/fcm-test/**").hasRole("ADMIN")

                        // --- Endpoints publics ---
                        // La poignee de main STOMP est publique, l'authentification du
                        // WebSocket est assuree par WebSocketAuthInterceptor (commande CONNECT).
                        .requestMatchers("/api/auth/**", "/ws/**", "/error", "/favicon.ico")
                        .permitAll()

                        // Pages legales (src/main/resources/static/legal/).
                        // Google Play exige qu'elles soient atteignables SANS
                        // installer l'application, et sans compte : ce sont les
                        // URL declarees dans "Contenu de l'application" et dans
                        // LegalLinks cote mobile. Derriere une authentification,
                        // elles rendraient 401 au reviseur.
                        .requestMatchers(HttpMethod.GET, "/legal", "/legal/", "/legal/**")
                        .permitAll()

                        // Politique de version de l'application mobile.
                        // Interrogee au demarrage, avant toute connexion: une
                        // version devenue incompatible avec le contrat de l'API
                        // est justement celle qui ne sait plus s'authentifier,
                        // et qui doit malgre tout apprendre qu'elle doit se
                        // mettre a jour. La reponse ne contient que des donnees
                        // publiques (numeros de version, fiches de magasin).
                        .requestMatchers(HttpMethod.GET, "/api/app/version")
                        .permitAll()

                        // Page web d'une annonce, pour le partage.
                        // Le destinataire d'un lien WhatsApp n'a, par
                        // definition, pas de compte: derriere une
                        // authentification, le lien ne montrerait rien et ne
                        // serait pas partage deux fois. La page ne rend que ce
                        // que rend deja le catalogue anonyme.
                        .requestMatchers(HttpMethod.GET, "/p/**")
                        .permitAll()

                        // --- Lecture publique: uniquement le catalogue ---
                        // Volontairement retires de cette liste:
                        //  - /api/notifications/**  : boites de reception privees
                        //  - /api/negotiations/**   : prix et volumes commerciaux
                        //  - /api/users/**          : donnees personnelles, enumeration de numeros
                        //
                        // Deux exceptions nominatives, et deux seulement:
                        //  - /api/users/{id}/public : la fiche vendeur, batie
                        //    sur un DTO qui ne porte ni numero ni role. C'est
                        //    la reponse a "a qui ai-je affaire ?", que le
                        //    catalogue anonyme doit pouvoir donner.
                        //  - /api/reviews/user/**   : les avis recus. Une note
                        //    qui ne se verrait qu'une fois connecte ne
                        //    rassurerait personne au moment ou l'on hesite.
                        // Le reste de /api/users/** et de /api/reviews/**
                        // (deposer un avis, lire ses veilles) reste ferme.
                        .requestMatchers(HttpMethod.GET,
                                "/api/categories/**",
                                "/api/products", "/api/products/{id}",
                                "/api/products/search", "/api/products/category/**", "/api/products/user/**",
                                "/api/products/{id}/similar", "/api/products/locations",
                                // La carte et la proximite se lisent sans
                                // compte, comme le catalogue : c'est le premier
                                // ecran qu'un visiteur regarde avant de decider
                                // s'il s'inscrit. Les positions qui en sortent
                                // sont arrondies pour tout le monde sauf leur
                                // auteur — un anonyme n'obtient donc rien de
                                // plus qu'un quartier.
                                "/api/products/map", "/api/products/nearby",
                                "/api/users/{id}/public",
                                "/api/reviews/user/**",
                                "/api/files/{filename:.+}")
                        .permitAll()

                        // Tout le reste exige une authentification
                        .anyRequest().authenticated()
                );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}