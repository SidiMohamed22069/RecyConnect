package com.project.RecyConnect.Config;


import com.project.RecyConnect.Model.UserSession;
import com.project.RecyConnect.Repository.UserSessionRepository;
import com.project.RecyConnect.Security.JwtUtil;
import com.project.RecyConnect.Service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserService userDetailsService;
    private final UserSessionRepository userSessionRepository;

    public JwtRequestFilter(JwtUtil jwtUtil, UserService userDetailsService, UserSessionRepository userSessionRepository) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.userSessionRepository = userSessionRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        
        // Ignorer les requêtes WebSocket pour éviter les boucles infinies
        String requestPath = request.getRequestURI();
        if (requestPath != null && (requestPath.startsWith("/ws") || requestPath.contains("/ws/"))) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        String token = null;
        String user_email = null;
        String deviceIdHeader = request.getHeader("X-Device-Id");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            try {
                user_email = jwtUtil.extractEmail(token);
            } catch (Exception e) {
                // Token invalide, continuer sans authentification
                filterChain.doFilter(request, response);
                return;
            }
        }
        if (user_email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(user_email);
                if (jwtUtil.validateToken(token, userDetails)) {
                    Long userId = jwtUtil.extractUserId(token);
                    Long sessionVersion = jwtUtil.extractSessionVersion(token);
                    String tokenDeviceId = jwtUtil.extractDeviceId(token);

                    if (userId == null || sessionVersion == null || tokenDeviceId == null || deviceIdHeader == null) {
                        filterChain.doFilter(request, response);
                        return;
                    }

                    UserSession activeSession = userSessionRepository.findById(userId).orElse(null);
                    if (activeSession == null) {
                        filterChain.doFilter(request, response);
                        return;
                    }

                    boolean sessionMatches = sessionVersion.equals(activeSession.getSessionVersion())
                            && tokenDeviceId.equals(activeSession.getDeviceId())
                            && deviceIdHeader.equals(activeSession.getDeviceId());

                    if (!sessionMatches) {
                        filterChain.doFilter(request, response);
                        return;
                    }

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (Exception e) {
                // Erreur d'authentification, continuer sans authentification
            }
        }
        filterChain.doFilter(request, response);
    }
}
