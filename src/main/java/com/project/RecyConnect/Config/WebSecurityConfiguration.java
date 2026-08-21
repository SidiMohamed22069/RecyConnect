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

                        // --- Lecture publique: uniquement le catalogue ---
                        // Volontairement retires de cette liste:
                        //  - /api/notifications/**  : boites de reception privees
                        //  - /api/negotiations/**   : prix et volumes commerciaux
                        //  - /api/users/**          : donnees personnelles, enumeration de numeros
                        .requestMatchers(HttpMethod.GET,
                                "/api/categories/**",
                                "/api/products", "/api/products/{id}",
                                "/api/products/search", "/api/products/category/**", "/api/products/user/**",
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