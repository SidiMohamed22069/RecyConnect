package com.project.RecyConnect.Config;


import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
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
public class WebSecurityConfiguration {

    private final JwtRequestFilter authFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf()
                .disable()
                .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeHttpRequests()
                // Permettre l'accès public aux endpoints d'authentification
                .requestMatchers("/api/auth/**", "/ws/**", "/error", "/favicon.ico")
                .permitAll()
                // Permettre l'accès public uniquement aux GET (lecture)
                .requestMatchers(HttpMethod.GET, 
                                "/api/categories/**", 
                                "/api/products", "/api/products/{id}", 
                                "/api/products/search", "/api/products/category/**", "/api/products/user/**",
                                "/api/negotiations", "/api/negotiations/{id}", "/api/negotiations/product/**",
                                "/api/negotiations/sender/**", "/api/negotiations/receiver/**",
                                "/api/users/{id}", "/api/users/by-phone/**", "/api/users/{id}/stats",
                                "/api/notifications/**", "/api/phone-verification/**", "/api/files/{filename:.+}")
                .permitAll()
                // Tous les autres endpoints (POST/PUT/PATCH/DELETE) nécessitent l'authentification
                .anyRequest()
                .authenticated()
                .and()
                .httpBasic();
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